/*
 * Copyright 2026 Flamingock (https://www.flamingock.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.flamingock.store.sql.internal;

import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.context.RuntimeContext;
import io.flamingock.internal.common.core.journal.JournalEvent;
import io.flamingock.internal.common.core.transaction.TransactionWrapper;
import io.flamingock.internal.common.sql.SqlDialect;
import io.flamingock.internal.common.sql.SqlDialectFactory;
import io.flamingock.internal.core.context.BasicRuntimeContext;
import io.flamingock.internal.core.journal.JournalEventStore;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * JDBC implementation of the local Journal Event buffer.
 *
 * <p>The store owns schema lifecycle and read/acknowledgement operations. Appends deliberately receive a
 * caller-owned connection so an audit current-state write and its event can share one transaction.</p>
 */
public class SqlJournalEventStore implements JournalEventStore {

    private final DataSource dataSource;
    private final String tableName;
    private final TransactionWrapper txWrapper;
    private SqlJournalEventMapper mapper;

    private SqlJournalDialectHelper dialectHelper;

    /**
     * Creates a journal store over a configured datasource.
     *
     * @param dataSource datasource used by schema and read operations
     * @param tableName  journal table name
     * @param txWrapper  SQL transaction wrapper that owns Journal writes
     */
    public SqlJournalEventStore(DataSource dataSource, String tableName, TransactionWrapper txWrapper) {
        if (dataSource == null) {
            throw new IllegalArgumentException("dataSource must not be null");
        }
        JournalEventConstants.validateIdentifier(tableName, "tableName");
        if (txWrapper == null) {
            throw new IllegalArgumentException("txWrapper must not be null");
        }
        this.dataSource = dataSource;
        this.tableName = tableName;
        this.txWrapper = txWrapper;
    }

    /**
     * Creates or validates the journal table and its indexes.
     *
     * @param autoCreate whether the table and indexes may be created when missing
     */
    public synchronized void initialize(boolean autoCreate) {
        try (Connection connection = dataSource.getConnection()) {
            dialectHelper = new SqlJournalDialectHelper(SqlDialectFactory.getSqlDialect(connection));
            mapper = new SqlJournalEventMapper(dialectHelper.getSqlDialect());
            if (!tableExists(connection.getMetaData())) {
                if (!autoCreate) {
                    throw new IllegalStateException("SQL journal table '" + tableName + "' does not exist");
                }
                createSchema(connection);
            }
            validateSchema(connection.getMetaData());
        } catch (SQLException exception) {
            throw sqlFailure("Failed to initialize SQL journal table '" + tableName + "'", exception);
        }
    }

    /**
     * Appends an immutable event using the supplied transaction-scoped connection.
     *
     * @param connection transaction-scoped connection owned by the caller
     * @param event      event to append
     */
    void append(Connection connection, JournalEvent<AuditEntry> event) {
        ensureInitialized();
        if (connection == null) {
            throw new IllegalArgumentException("connection must not be null");
        }
        try (PreparedStatement statement = connection.prepareStatement(dialectHelper.getInsertSqlString(tableName))) {
            mapper.bind(statement, event);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw sqlFailure("Failed to append SQL journal event", exception);
        }
    }

    @Override
    public Optional<JournalEvent<AuditEntry>> getLastEventByStream(String streamId) {
        ensureInitialized();
        if (streamId == null || streamId.trim().isEmpty()) {
            return Optional.empty();
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     dialectHelper.getLastEventSqlString(tableName))) {
            statement.setString(1, streamId);
            statement.setMaxRows(1);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(mapper.fromResultSet(resultSet)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw sqlFailure("Failed to read last SQL journal event", exception);
        }
    }

    @Override
    public List<JournalEvent<AuditEntry>> getUnacknowledgedEvents(int limit) {
        ensureInitialized();
        if (limit <= 0) {
            return Collections.emptyList();
        }

        List<JournalEvent<AuditEntry>> events = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     dialectHelper.getUnacknowledgedEventsSqlString(tableName))) {
            statement.setBoolean(1, false);
            statement.setMaxRows(limit);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    events.add(mapper.fromResultSet(resultSet));
                }
            }
            return events;
        } catch (SQLException exception) {
            throw sqlFailure("Failed to read unacknowledged SQL journal events", exception);
        }
    }

    @Override
    public long acknowledgeEvents(Collection<String> eventIds) {
        ensureInitialized();
        if (eventIds == null || eventIds.isEmpty()) {
            return 0L;
        }

        Set<String> validEventIds = new LinkedHashSet<>();
        for (String eventId : eventIds) {
            if (eventId != null && !eventId.trim().isEmpty()) {
                validEventIds.add(eventId);
            }
        }
        if (validEventIds.isEmpty()) {
            return 0L;
        }

        long acknowledged = 0L;
        for (String eventId : validEventIds) {
            RuntimeContext baseContext = new BasicRuntimeContext(
                    "acknowledge-journal-event-" + UUID.randomUUID());
            acknowledged += txWrapper.wrapInTransaction(baseContext, runtimeContext -> {
                Connection connection = runtimeContext.getContext().getRequiredDependencyValue(Connection.class);
                return acknowledgeEvents(connection, Collections.singleton(eventId));
            });
        }
        return acknowledged;
    }

    private long acknowledgeEvents(Connection connection, Collection<String> eventIds) {
        long acknowledged = 0L;
        try (PreparedStatement statement = connection.prepareStatement(
                dialectHelper.getAcknowledgeSqlString(tableName))) {
            for (String eventId : eventIds) {
                statement.setBoolean(1, true);
                statement.setString(2, eventId);
                statement.setBoolean(3, false);
                acknowledged += statement.executeUpdate();
            }
            return acknowledged;
        } catch (SQLException exception) {
            throw sqlFailure("Failed to acknowledge SQL journal events", exception);
        }
    }

    private void createSchema(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                dialectHelper.getCreateTableSqlString(tableName))) {
            statement.executeUpdate();
        }
        for (String indexSql : dialectHelper.getCreateIndexSqlStrings(tableName)) {
            try (PreparedStatement statement = connection.prepareStatement(indexSql)) {
                statement.executeUpdate();
            }
        }
    }

    private void validateSchema(DatabaseMetaData metadata) throws SQLException {
        List<ColumnMetadata> actualColumns = readColumns(metadata);
        List<SqlJournalDialectHelper.ColumnDefinition> expectedColumns = dialectHelper.getColumnDefinitions();
        Set<String> expectedColumnNames = new HashSet<>();
        for (SqlJournalDialectHelper.ColumnDefinition expected : expectedColumns) {
            expectedColumnNames.add(expected.name.toLowerCase(Locale.ROOT));
        }
        for (ColumnMetadata actual : actualColumns) {
            if (!expectedColumnNames.contains(actual.name.toLowerCase(Locale.ROOT))
                    && actual.blocksExplicitInsert()) {
                throw new IllegalStateException("SQL journal table '" + tableName
                        + "' has extra non-null column '" + actual.name
                        + "' without a default, identity, or generated value");
            }
        }

        Map<String, List<ColumnMetadata>> columnsByName = new HashMap<>();
        for (ColumnMetadata actual : actualColumns) {
            columnsByName.computeIfAbsent(actual.name.toLowerCase(Locale.ROOT), ignored -> new ArrayList<>())
                    .add(actual);
        }

        for (SqlJournalDialectHelper.ColumnDefinition expected : expectedColumns) {
            List<ColumnMetadata> matches = columnsByName.get(expected.name.toLowerCase(Locale.ROOT));
            if (matches == null || matches.isEmpty()) {
                throw new IllegalStateException("SQL journal table '" + tableName
                        + "' is missing required column '" + expected.name + "'");
            }
            if (matches.size() > 1) {
                throw new IllegalStateException("SQL journal table '" + tableName
                        + "' has ambiguous required column '" + expected.name + "'");
            }
            ColumnMetadata actual = matches.get(0);
            if (actual.nullable != (expected.nullable
                    ? DatabaseMetaData.columnNullable : DatabaseMetaData.columnNoNulls)) {
                throw new IllegalStateException("SQL journal table '" + tableName
                        + "' has incorrect nullability for column '" + expected.name + "'");
            }
            if (!matchesColumnType(expected, actual)) {
                throw new IllegalStateException("SQL journal table '" + tableName
                        + "' has incorrect type or capacity for column '" + expected.name + "'");
            }
        }

        validateIndexes(metadata);
        validatePrimaryKey(metadata);
    }

    private List<ColumnMetadata> readColumns(DatabaseMetaData metadata) throws SQLException {
        List<ColumnMetadata> columns = new ArrayList<>();
        try (ResultSet resultSet = metadata.getColumns(null, null, null, null)) {
            while (resultSet.next()) {
                if (tableName.equalsIgnoreCase(resultSet.getString("TABLE_NAME"))) {
                    columns.add(new ColumnMetadata(
                            resultSet.getString("COLUMN_NAME"),
                            resultSet.getInt("DATA_TYPE"),
                            resultSet.getString("TYPE_NAME"),
                            resultSet.getInt("COLUMN_SIZE"),
                            resultSet.getInt("NULLABLE"),
                            resultSet.getInt("ORDINAL_POSITION"),
                            readOptionalMetadata(resultSet, "COLUMN_DEF"),
                            readOptionalMetadata(resultSet, "IS_AUTOINCREMENT"),
                            readOptionalMetadata(resultSet, "IS_GENERATEDCOLUMN")));
                }
            }
        }
        columns.sort(Comparator.comparingInt(column -> column.ordinalPosition));
        return columns;
    }

    private OptionalMetadata readOptionalMetadata(ResultSet resultSet, String columnName) {
        try {
            return new OptionalMetadata(true, resultSet.getString(columnName));
        } catch (SQLException | RuntimeException exception) {
            return new OptionalMetadata(false, null);
        }
    }

    private Map<String, IndexMetadata> readIndexes(DatabaseMetaData metadata) throws SQLException {
        Map<String, IndexMetadata> indexes = new HashMap<>();
        for (String candidate : tableNameCandidates()) {
            try (ResultSet resultSet = metadata.getIndexInfo(null, null, candidate, false, false)) {
                while (resultSet.next()) {
                    if (tableName.equalsIgnoreCase(resultSet.getString("TABLE_NAME"))) {
                        String indexName = resultSet.getString("INDEX_NAME");
                        String columnName = resultSet.getString("COLUMN_NAME");
                        if (indexName != null && columnName != null) {
                            String key = indexName.toLowerCase(Locale.ROOT);
                            IndexMetadata index = indexes.get(key);
                            if (index == null) {
                                index = new IndexMetadata();
                                indexes.put(key, index);
                            }
                            index.nonUnique = resultSet.getBoolean("NON_UNIQUE");
                            index.columns.put(resultSet.getShort("ORDINAL_POSITION"), columnName);
                        }
                    }
                }
            }
        }
        return indexes;
    }

    private void validateIndexes(DatabaseMetaData metadata) throws SQLException {
        Map<String, IndexMetadata> indexes = readIndexes(metadata);
        List<String> names = dialectHelper.getIndexNames(tableName);
        List<List<String>> expectedColumns = new ArrayList<>();
        expectedColumns.add(asList(JournalEventConstants.ACKNOWLEDGED,
                JournalEventConstants.STREAM_ID, JournalEventConstants.STREAM_SEQUENCE));
        expectedColumns.add(asList(JournalEventConstants.EVENT_ID));

        for (int i = 0; i < names.size(); i++) {
            IndexMetadata index = indexes.get(names.get(i).toLowerCase(Locale.ROOT));
            if (index == null) {
                throw new IllegalStateException("SQL journal table '" + tableName
                        + "' is missing index '" + names.get(i) + "'");
            }
            if (!index.nonUnique || !sameColumns(index.columnsInOrder(), expectedColumns.get(i))) {
                throw new IllegalStateException("SQL journal table '" + tableName
                        + "' has incorrect shape for index '" + names.get(i) + "'");
            }
        }
    }

    private void validatePrimaryKey(DatabaseMetaData metadata) throws SQLException {
        Map<Short, String> primaryKeyColumns = new HashMap<>();
        for (String candidate : tableNameCandidates()) {
            try (ResultSet resultSet = metadata.getPrimaryKeys(null, null, candidate)) {
                while (resultSet.next()) {
                    if (tableName.equalsIgnoreCase(resultSet.getString("TABLE_NAME"))) {
                        primaryKeyColumns.put(resultSet.getShort("KEY_SEQ"), resultSet.getString("COLUMN_NAME"));
                    }
                }
            }
        }
        if (primaryKeyColumns.size() != 2
                || !JournalEventConstants.STREAM_ID.equalsIgnoreCase(primaryKeyColumns.get((short) 1))
                || !JournalEventConstants.STREAM_SEQUENCE.equalsIgnoreCase(primaryKeyColumns.get((short) 2))) {
            throw new IllegalStateException("SQL journal table '" + tableName
                    + "' must have primary key (stream_id, stream_sequence)");
        }
    }

    private String[] tableNameCandidates() {
        return new String[]{tableName, tableName.toUpperCase(), tableName.toLowerCase()};
    }

    private boolean tableExists(DatabaseMetaData metadata) throws SQLException {
        try (ResultSet resultSet = metadata.getTables(null, null, null, new String[]{"TABLE"})) {
            while (resultSet.next()) {
                if (tableName.equalsIgnoreCase(resultSet.getString("TABLE_NAME"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private void ensureInitialized() {
        if (dialectHelper == null || mapper == null) {
            throw new IllegalStateException("SQL journal store is not initialized");
        }
    }

    private IllegalStateException sqlFailure(String message, SQLException exception) {
        return new IllegalStateException(message, exception);
    }

    private boolean matchesColumnType(SqlJournalDialectHelper.ColumnDefinition expected,
                                      ColumnMetadata actual) {
        switch (expected.type) {
            case VARCHAR:
                return actual.jdbcType == java.sql.Types.VARCHAR && actual.columnSize == expected.size;
            case INTEGER:
                return actual.jdbcType == java.sql.Types.INTEGER
                        || (dialectHelper.getSqlDialect() == SqlDialect.ORACLE
                        && isNumeric(actual.jdbcType));
            case LONG:
                return (actual.jdbcType == java.sql.Types.BIGINT && actual.columnSize >= expected.size)
                        || (dialectHelper.getSqlDialect() == SqlDialect.SQLITE
                        && actual.jdbcType == java.sql.Types.INTEGER)
                        || (dialectHelper.getSqlDialect() == SqlDialect.ORACLE
                        && isNumeric(actual.jdbcType) && actual.columnSize >= expected.size);
            case TIMESTAMP:
                return actual.jdbcType == java.sql.Types.TIMESTAMP
                        || (dialectHelper.getSqlDialect() == SqlDialect.SQLITE
                        && "TIMESTAMP".equalsIgnoreCase(actual.typeName));
            case BOOLEAN:
                return actual.jdbcType == dialectHelper.getBooleanJdbcType()
                        || isBooleanDriverAlias(actual);
            case TEXT:
                if (actual.jdbcType == java.sql.Types.CLOB || actual.jdbcType == java.sql.Types.NCLOB) {
                    return false;
                }
                return (actual.jdbcType == java.sql.Types.VARCHAR
                        || actual.jdbcType == java.sql.Types.LONGVARCHAR)
                        && (actual.columnSize >= expected.size
                        || "TEXT".equalsIgnoreCase(actual.typeName));
            default:
                return false;
        }
    }

    private boolean isNumeric(int jdbcType) {
        return jdbcType == java.sql.Types.NUMERIC || jdbcType == java.sql.Types.DECIMAL;
    }

    private boolean isBooleanDriverAlias(ColumnMetadata actual) {
        switch (dialectHelper.getSqlDialect()) {
            case MYSQL:
            case MARIADB:
                return actual.jdbcType == java.sql.Types.BIT
                        || actual.jdbcType == java.sql.Types.BOOLEAN;
            case POSTGRESQL:
            case INFORMIX:
                return actual.jdbcType == java.sql.Types.BIT;
            default:
                return false;
        }
    }

    private static List<String> asList(String... values) {
        return new ArrayList<>(java.util.Arrays.asList(values));
    }

    private static boolean sameColumns(List<String> actual, List<String> expected) {
        if (actual.size() != expected.size()) {
            return false;
        }
        for (int i = 0; i < actual.size(); i++) {
            if (!actual.get(i).equalsIgnoreCase(expected.get(i))) {
                return false;
            }
        }
        return true;
    }

    private static final class ColumnMetadata {
        private final String name;
        private final int jdbcType;
        private final String typeName;
        private final int columnSize;
        private final int nullable;
        private final int ordinalPosition;
        private final OptionalMetadata columnDefault;
        private final OptionalMetadata autoIncrement;
        private final OptionalMetadata generated;

        private ColumnMetadata(String name,
                               int jdbcType,
                               String typeName,
                               int columnSize,
                               int nullable,
                               int ordinalPosition,
                               OptionalMetadata columnDefault,
                               OptionalMetadata autoIncrement,
                               OptionalMetadata generated) {
            this.name = name;
            this.jdbcType = jdbcType;
            this.typeName = typeName;
            this.columnSize = columnSize;
            this.nullable = nullable;
            this.ordinalPosition = ordinalPosition;
            this.columnDefault = columnDefault;
            this.autoIncrement = autoIncrement;
            this.generated = generated;
        }

        private boolean blocksExplicitInsert() {
            return nullable == DatabaseMetaData.columnNoNulls
                    && columnDefault.available
                    && !hasUsableDefault()
                    && isNo(autoIncrement)
                    && isNo(generated);
        }

        private boolean hasUsableDefault() {
            return columnDefault.value != null
                    && !"NULL".equalsIgnoreCase(columnDefault.value.trim());
        }

        private boolean isNo(OptionalMetadata metadata) {
            return metadata.available && "NO".equalsIgnoreCase(metadata.value);
        }
    }

    private static final class OptionalMetadata {
        private final boolean available;
        private final String value;

        private OptionalMetadata(boolean available, String value) {
            this.available = available;
            this.value = value;
        }
    }

    private static final class IndexMetadata {
        private boolean nonUnique;
        private final Map<Short, String> columns = new HashMap<>();

        private List<String> columnsInOrder() {
            List<Map.Entry<Short, String>> entries = new ArrayList<>(columns.entrySet());
            entries.sort(Map.Entry.comparingByKey());
            List<String> orderedColumns = new ArrayList<>();
            for (Map.Entry<Short, String> entry : entries) {
                orderedColumns.add(entry.getValue());
            }
            return orderedColumns;
        }
    }
}
