/*
 * Copyright 2025 Flamingock (https://www.flamingock.io)
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
package io.flamingock.store.sql;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.flamingock.common.test.pipeline.CodeChangeTestDefinition;
import io.flamingock.core.kit.audit.AuditEntryTestFactory;
import io.flamingock.core.kit.TestKit;
import io.flamingock.core.kit.audit.AuditTestSupport;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.audit.AuditTxType;
import io.flamingock.internal.common.core.error.FlamingockException;
import io.flamingock.internal.common.core.feature.Features;
import io.flamingock.internal.core.external.store.audit.community.CommunityAuditPersistence;
import io.flamingock.internal.core.configuration.community.CommunityConfiguration;
import io.flamingock.internal.core.context.SimpleContext;
import io.flamingock.internal.util.FeatureFlag;
import io.flamingock.internal.util.id.RunnerId;
import io.flamingock.internal.common.sql.SqlDialect;
import io.flamingock.internal.core.operation.OperationException;
import io.flamingock.store.sql.changes.postgresql.failedWithoutRollback._001__create_index;
import io.flamingock.store.sql.changes.postgresql.failedWithoutRollback._002__insert_document;
import io.flamingock.store.sql.changes.postgresql.failedWithoutRollback._003__execution_with_exception;
import io.flamingock.store.sql.changes.postgresql.happyPath._003__insert_another_document;
import io.flamingock.targetsystem.sql.SqlTargetSystem;
import io.flamingock.sql.kit.SqlTestKit;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.sqlite.SQLiteDataSource;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.mockito.MockedStatic;

import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.flamingock.core.kit.audit.AuditEntryExpectation.*;
import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Testcontainers
class SqlAuditStoreTest {

    private static final Map<String, JdbcDatabaseContainer<?>> containers = new HashMap<>();
    private static final Map<String, DataSource> dataSources = new HashMap<>();
    private TestContext context;

    static Stream<Arguments> dialectProvider() {
        String enabledDialects = System.getProperty("sql.test.dialects", "mysql");
        Set<String> enabled = Arrays.stream(enabledDialects.split(","))
                .map(String::trim)
                .collect(Collectors.toSet());

        Stream<Arguments> allDialects = Stream.of(
                Arguments.of(SqlDialect.MYSQL, "mysql"),
                Arguments.of(SqlDialect.SQLSERVER, "sqlserver"),
                Arguments.of(SqlDialect.ORACLE, "oracle"),
                Arguments.of(SqlDialect.POSTGRESQL, "postgresql"),
                Arguments.of(SqlDialect.MARIADB, "mariadb"),
                Arguments.of(SqlDialect.H2, "h2"),
                Arguments.of(SqlDialect.SQLITE, "sqlite"),
                Arguments.of(SqlDialect.INFORMIX, "informix"),
                Arguments.of(SqlDialect.FIREBIRD, "firebird")
        );

        return allDialects.filter(args -> {
            String dialectName = (String) args.get()[1];
            return enabled.contains(dialectName);
        });
    }


    @BeforeAll
    void startContainers() {
        for (Arguments arg : dialectProvider().toArray(Arguments[]::new)) {
            SqlDialect dialect = (SqlDialect) arg.get()[0];
            String dialectName = (String) arg.get()[1];
            if (!"h2".equals(dialectName) && !"sqlite".equals(dialectName)) {
                JdbcDatabaseContainer<?> container = SqlAuditTestHelper.createContainer(dialectName);
                container.start();
                containers.put(dialectName, container);
                dataSources.put(dialectName, SqlAuditTestHelper.createDataSource(container));
            }
        }
    }

    @AfterEach
    void tearDown() throws SQLException {
        FeatureFlag.remove(Features.JOURNAL_EVENTS);
        if (context != null) {
            context.cleanup();
        }
    }

    @AfterAll
    void stopContainers() {
        containers.values().forEach(JdbcDatabaseContainer::stop);
        dataSources.values().forEach(ds -> {
            if (ds instanceof HikariDataSource) {
                ((HikariDataSource) ds).close();
            }
        });
    }

    private TestContext setupTest(SqlDialect sqlDialect, String dialectName) throws SQLException {
        if ("h2".equals(dialectName)) {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl("jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1");
            config.setUsername("sa");
            config.setPassword("");
            config.setDriverClassName("org.h2.Driver");
            DataSource dataSource = new HikariDataSource(config);

            SqlAuditTestHelper.createTables(dataSource, sqlDialect);

            return new TestContext(dataSource, null, sqlDialect);
        }

        if ("sqlite".equals(dialectName)) {
            String dbFile = "test_" + System.currentTimeMillis() + ".db";

            // Use a shared in-memory DB or file DB, but single connection
            String jdbcUrl = "jdbc:sqlite:" + dbFile;

            // Create a single-connection DataSource for SQLite
            SQLiteDataSource ds = new SQLiteDataSource();
            ds.setUrl(jdbcUrl);

            try (Connection conn = ds.getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.execute("PRAGMA journal_mode=WAL;");
                stmt.execute("PRAGMA busy_timeout=5000;");
            }

            // Run table creation with this same DataSource
            SqlAuditTestHelper.createTables(ds, sqlDialect);

            return new TestContext(ds, null, SqlDialect.SQLITE);
        }

        JdbcDatabaseContainer<?> container = SqlAuditTestHelper.createContainer(dialectName);
        container.start();

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(container.getJdbcUrl());
        config.setUsername(container.getUsername());
        config.setPassword(container.getPassword());
        config.setDriverClassName(container.getDriverClassName());
        DataSource dataSource = new HikariDataSource(config);

        SqlAuditTestHelper.createTables(dataSource, sqlDialect);

        return new TestContext(dataSource, container, sqlDialect);
    }

    private Class<?>[] getChangeClasses(String dialectName, String scenario) {
        switch (dialectName) {
            case "mysql":
            case "mariadb":
            case "sqlite":
                case "informix":
            case "h2":
                if ("happyPath".equals(scenario)) {
                    return new Class<?>[]{
                            io.flamingock.store.sql.changes.mysql.happyPath._001__create_index.class,
                            io.flamingock.store.sql.changes.mysql.happyPath._002__insert_document.class,
                            io.flamingock.store.sql.changes.mysql.happyPath._003__insert_another_document.class
                    };
                } else if ("failedWithRollback".equals(scenario)) {
                    return new Class<?>[]{
                            io.flamingock.store.sql.changes.mysql.failedWithRollback._001__create_index.class,
                            io.flamingock.store.sql.changes.mysql.failedWithRollback._002__insert_document.class,
                            io.flamingock.store.sql.changes.mysql.failedWithRollback._003__execution_with_exception.class
                    };
                } else if ("failedWithoutRollback".equals(scenario)) {
                    return new Class<?>[]{
                            io.flamingock.store.sql.changes.mysql.failedWithoutRollback._001__create_index.class,
                            io.flamingock.store.sql.changes.mysql.failedWithoutRollback._002__insert_document.class,
                            io.flamingock.store.sql.changes.mysql.failedWithoutRollback._003__execution_with_exception.class
                    };
                }
                break;
            case "sqlserver":
                if ("happyPath".equals(scenario)) {
                    return new Class<?>[]{
                            io.flamingock.store.sql.changes.sqlserver.happyPath._001__create_index.class,
                            io.flamingock.store.sql.changes.sqlserver.happyPath._002__insert_document.class,
                            io.flamingock.store.sql.changes.sqlserver.happyPath._003__insert_another_document.class
                    };
                } else if ("failedWithRollback".equals(scenario)) {
                    return new Class<?>[]{
                            io.flamingock.store.sql.changes.sqlserver.failedWithRollback._001__create_index.class,
                            io.flamingock.store.sql.changes.sqlserver.failedWithRollback._002__insert_document.class,
                            io.flamingock.store.sql.changes.sqlserver.failedWithRollback._003__execution_with_exception.class
                    };
                } else if ("failedWithoutRollback".equals(scenario)) {
                    return new Class<?>[]{
                            io.flamingock.store.sql.changes.sqlserver.failedWithoutRollback._001__create_index.class,
                            io.flamingock.store.sql.changes.sqlserver.failedWithoutRollback._002__insert_document.class,
                            io.flamingock.store.sql.changes.sqlserver.failedWithoutRollback._003__execution_with_exception.class
                    };
                }
                break;
            case "oracle":
                if ("happyPath".equals(scenario)) {
                    return new Class<?>[]{
                            io.flamingock.store.sql.changes.oracle.happyPath._001__create_index.class,
                            io.flamingock.store.sql.changes.oracle.happyPath._002__insert_document.class,
                            io.flamingock.store.sql.changes.oracle.happyPath._003__insert_another_document.class
                    };
                } else if ("failedWithRollback".equals(scenario)) {
                    return new Class<?>[]{
                            io.flamingock.store.sql.changes.oracle.failedWithRollback._001__create_index.class,
                            io.flamingock.store.sql.changes.oracle.failedWithRollback._002__insert_document.class,
                            io.flamingock.store.sql.changes.oracle.failedWithRollback._003__execution_with_exception.class
                    };
                } else if ("failedWithoutRollback".equals(scenario)) {
                    return new Class<?>[]{
                            io.flamingock.store.sql.changes.oracle.failedWithoutRollback._001__create_index.class,
                            io.flamingock.store.sql.changes.oracle.failedWithoutRollback._002__insert_document.class,
                            io.flamingock.store.sql.changes.oracle.failedWithoutRollback._003__execution_with_exception.class
                    };
                }
                break;
            case "firebird":
                if ("happyPath".equals(scenario)) {
                    return new Class<?>[]{
                            io.flamingock.store.sql.changes.firebird.happyPath._001__create_index.class,
                            io.flamingock.store.sql.changes.firebird.happyPath._002__insert_document.class,
                            io.flamingock.store.sql.changes.firebird.happyPath._003__insert_another_document.class
                    };
                } else if ("failedWithRollback".equals(scenario)) {
                    return new Class<?>[]{
                            io.flamingock.store.sql.changes.firebird.failedWithRollback._001__create_index.class,
                            io.flamingock.store.sql.changes.firebird.failedWithRollback._002__insert_document.class,
                            io.flamingock.store.sql.changes.firebird.failedWithRollback._003__execution_with_exception.class
                    };
                } else if ("failedWithoutRollback".equals(scenario)) {
                    return new Class<?>[]{
                            io.flamingock.store.sql.changes.firebird.failedWithoutRollback._001__create_index.class,
                            io.flamingock.store.sql.changes.firebird.failedWithoutRollback._002__insert_document.class,
                            io.flamingock.store.sql.changes.firebird.failedWithoutRollback._003__execution_with_exception.class
                    };
                }
                break;
            case "postgresql":
            case "db2":
                if ("happyPath".equals(scenario)) {
                    return new Class<?>[]{
                            io.flamingock.store.sql.changes.postgresql.happyPath._001__create_index.class,
                            io.flamingock.store.sql.changes.postgresql.happyPath._002__insert_document.class,
                            _003__insert_another_document.class
                    };
                } else if ("failedWithRollback".equals(scenario)) {
                    return new Class<?>[]{
                            io.flamingock.store.sql.changes.postgresql.failedWithRollback._001__create_index.class,
                            io.flamingock.store.sql.changes.postgresql.failedWithRollback._002__insert_document.class,
                            io.flamingock.store.sql.changes.postgresql.failedWithRollback._003__execution_with_exception.class
                    };
                } else if ("failedWithoutRollback".equals(scenario)) {
                    return new Class<?>[]{
                            _001__create_index.class,
                            _002__insert_document.class,
                            _003__execution_with_exception.class
                    };
                }
                break;
        }
        throw new IllegalArgumentException("Unsupported dialect/scenario: " + dialectName + "/" + scenario);
    }

    @ParameterizedTest
    @MethodSource("dialectProvider")
    @DisplayName("When standalone runs the AuditStore should persist the audit logs and the test data")
    void happyPathWithMockedPipeline(SqlDialect sqlDialect, String dialectName) throws Exception {
        context = setupTest(sqlDialect, dialectName);
        SqlTargetSystem sqlTargetSystem = new SqlTargetSystem("sql", context.dataSource);
        SqlAuditStore sqlAuditStore = SqlAuditStore.from(sqlTargetSystem);
        TestKit testKit = SqlTestKit.create(sqlAuditStore, context.dataSource);

        Class<?>[] changeClasses = getChangeClasses(dialectName, "happyPath");
        String[] expectedChangeIds = {"create-index", "insert-document", "insert-another-document"};
        //Given-When-Then
        AuditTestSupport.withTestKit(testKit)
            .GIVEN_Changes(
                new CodeChangeTestDefinition(changeClasses[0], Collections.singletonList(Connection.class)),
                new CodeChangeTestDefinition(changeClasses[1], Collections.singletonList(Connection.class)),
                new CodeChangeTestDefinition(changeClasses[2], Collections.singletonList(Connection.class))
            )
            .WHEN(() -> testKit.createBuilder()
                .setAuditStore(sqlAuditStore)
                .addTargetSystem(sqlTargetSystem)
                .build()
                .run())
            .THEN_VerifyAuditSequenceStrict(
                STARTED(expectedChangeIds[0]),
                APPLIED(expectedChangeIds[0]),
                STARTED(expectedChangeIds[1]),
                APPLIED(expectedChangeIds[1]),
                STARTED(expectedChangeIds[2]),
                APPLIED(expectedChangeIds[2])
            )
            .run();

        // Verify index exists and data state
        SqlAuditTestHelper.verifyIndexExists(context);
        verifyDataState(context, false);
    }

    @ParameterizedTest
    @MethodSource("dialectProvider")
    @DisplayName("When standalone runs the AuditStore and execution fails (with rollback method) should persist all the audit logs up to the failed one (ROLLED_BACK)")
    void failedWithRollback(SqlDialect sqlDialect, String dialectName) throws Exception {
        context = setupTest(sqlDialect, dialectName);
        SqlTargetSystem sqlTargetSystem = new SqlTargetSystem("sql", context.dataSource);
        SqlAuditStore sqlAuditStore = SqlAuditStore.from(sqlTargetSystem);
        TestKit testKit = SqlTestKit.create(sqlAuditStore, context.dataSource);

        Class<?>[] changeClasses = getChangeClasses(dialectName, "failedWithRollback");
        String[] expectedChangeIds = {"create-index", "insert-document", "execution-with-exception"};
        //Given-When-Then
        AuditTestSupport.withTestKit(testKit)
            .GIVEN_Changes(
                new CodeChangeTestDefinition(changeClasses[0], Collections.singletonList(Connection.class), null),
                new CodeChangeTestDefinition(changeClasses[1], Collections.singletonList(Connection.class), Collections.singletonList(Connection.class)),
                new CodeChangeTestDefinition(changeClasses[2], Collections.singletonList(Connection.class), Collections.singletonList(Connection.class))
            )
            .WHEN(() -> assertThrows(OperationException.class, () -> {
                testKit.createBuilder()
                    .setAuditStore(sqlAuditStore)
                    .addTargetSystem(sqlTargetSystem)
                    .build()
                    .run();
            }))
            .THEN_VerifyAuditSequenceStrict(
                STARTED(expectedChangeIds[0]),
                APPLIED(expectedChangeIds[0]),
                STARTED(expectedChangeIds[1]),
                APPLIED(expectedChangeIds[1]),
                STARTED(expectedChangeIds[2]),
                FAILED(expectedChangeIds[2]),
                ROLLED_BACK(expectedChangeIds[2])
            )
            .run();

        // Verify index exists and data state
        SqlAuditTestHelper.verifyIndexExists(context);
        verifyDataState(context, true);
    }

    @ParameterizedTest
    @MethodSource("dialectProvider")
    @DisplayName("When standalone runs the AuditStore and execution fails (without rollback method) should persist all the audit logs up to the failed one (FAILED)")
    void failedWithoutRollback(SqlDialect sqlDialect, String dialectName) throws Exception {
        context = setupTest(sqlDialect, dialectName);
        SqlTargetSystem sqlTargetSystem = new SqlTargetSystem("sql", context.dataSource);
        SqlAuditStore sqlAuditStore = SqlAuditStore.from(sqlTargetSystem);
        TestKit testKit = SqlTestKit.create(sqlAuditStore, context.dataSource);

        Class<?>[] changeClasses = getChangeClasses(dialectName, "failedWithoutRollback");
        String[] expectedChangeIds = {"create-index", "insert-document", "execution-with-exception"};
        //Given-When-Then
        AuditTestSupport.withTestKit(testKit)
            .GIVEN_Changes(
                new CodeChangeTestDefinition(changeClasses[0], Collections.singletonList(Connection.class), null),
                new CodeChangeTestDefinition(changeClasses[1], Collections.singletonList(Connection.class), null),
                new CodeChangeTestDefinition(changeClasses[2], Collections.singletonList(Connection.class), null)
            )
            .WHEN(() -> assertThrows(OperationException.class, () -> {
                testKit.createBuilder()
                    .setAuditStore(sqlAuditStore)
                    .addTargetSystem(sqlTargetSystem)
                    .build()
                    .run();
            }))
            .THEN_VerifyAuditSequenceStrict(
                STARTED(expectedChangeIds[0]),
                APPLIED(expectedChangeIds[0]),
                STARTED(expectedChangeIds[1]),
                APPLIED(expectedChangeIds[1]),
                STARTED(expectedChangeIds[2]),
                FAILED(expectedChangeIds[2]),
                ROLLED_BACK(expectedChangeIds[2])
            )
            .run();

        // Verify index exists and data state
        SqlAuditTestHelper.verifyIndexExists(context);
        verifyDataState(context, true);
    }

    @Test
    @DisplayName("When journal events are enabled the SQL store creates a stage-scoped journal beside current audit state")
    void journalEnabledUsesStageScopedPersistenceAndIndependentReader() throws Exception {
        FeatureFlag.enable(Features.JOURNAL_EVENTS);
        context = setupTest(SqlDialect.SQLITE, "sqlite");

        SimpleContext baseContext = new SimpleContext();
        baseContext.addDependency(RunnerId.generate());
        baseContext.addDependency(new CommunityConfiguration());
        SqlTargetSystem targetSystem = new SqlTargetSystem("sql", context.dataSource);
        targetSystem.initialize(baseContext);

        SqlAuditStore auditStore = SqlAuditStore.from(targetSystem)
                .withAuditRepositoryName("flamingockAuditLog")
                .withLockRepositoryName("flamingockLock")
                .withJournalRepositoryName("customJournalEvents");
        auditStore.initialize(baseContext);

        CommunityAuditPersistence persistence = auditStore.getPersistenceFactory().get("stage-one");
        persistence.writeEntry(auditEntry("journal-change", AuditEntry.Status.STARTED));
        persistence.writeEntry(auditEntry("journal-change", AuditEntry.Status.APPLIED));

        assertEquals(1, auditStore.getAuditReader().getAuditHistory().size());
        assertEquals(1, countRows("flamingockAuditLog"));
        assertEquals(2, countRows("customJournalEvents"));
    }

    @ParameterizedTest
    @MethodSource("dialectProvider")
    @DisplayName("journal-enabled writes round-trip through every runtime SQL dialect")
    void journalEnabledRoundTripsAcrossRuntimeDialects(SqlDialect sqlDialect, String dialectName) throws Exception {
        FeatureFlag.enable(Features.JOURNAL_EVENTS);
        context = setupTest(sqlDialect, dialectName);

        SimpleContext baseContext = new SimpleContext();
        baseContext.addDependency(RunnerId.generate());
        baseContext.addDependency(new CommunityConfiguration());
        SqlTargetSystem targetSystem = new SqlTargetSystem("sql", context.dataSource);
        targetSystem.initialize(baseContext);
        SqlAuditStore auditStore = SqlAuditStore.from(targetSystem);
        auditStore.initialize(baseContext);

        CommunityAuditPersistence persistence = auditStore.getPersistenceFactory().get("matrix-stage");
        persistence.writeEntry(auditEntry("matrix-change", AuditEntry.Status.STARTED));
        persistence.writeEntry(auditEntry("matrix-change", AuditEntry.Status.APPLIED));

        assertEquals(1, auditStore.getAuditReader().getAuditHistory().size());
        assertEquals(2, countRows("flamingockJournalEvents"));
    }

    @Test
    @DisplayName("keeps the default Journal repository name private to the SQL audit store")
    void keepsDefaultJournalRepositoryNamePrivateToSqlAuditStore() throws Exception {
        Field defaultRepositoryName = SqlAuditStore.class.getDeclaredField("DEFAULT_JOURNAL_REPOSITORY_NAME");

        assertTrue(Modifier.isPrivate(defaultRepositoryName.getModifiers()));
        assertTrue(Modifier.isStatic(defaultRepositoryName.getModifiers()));
        assertTrue(Modifier.isFinal(defaultRepositoryName.getModifiers()));
        defaultRepositoryName.setAccessible(true);
        assertEquals("flamingockJournalEvents", defaultRepositoryName.get(null));
    }

    @Test
    @DisplayName("The journal repository name cannot collide with an audit or lock repository")
    void journalRepositoryNameMustBeDistinct() throws Exception {
        context = setupTest(SqlDialect.SQLITE, "sqlite");
        SimpleContext baseContext = new SimpleContext();
        baseContext.addDependency(RunnerId.generate());
        baseContext.addDependency(new CommunityConfiguration());
        SqlTargetSystem targetSystem = new SqlTargetSystem("sql", context.dataSource);
        targetSystem.initialize(baseContext);

        SqlAuditStore auditStore = SqlAuditStore.from(targetSystem)
                .withAuditRepositoryName("sameRepository")
                .withLockRepositoryName("differentRepository")
                .withJournalRepositoryName("sameRepository");

        assertThrows(FlamingockException.class, () -> auditStore.initialize(baseContext));
    }

    @Test
    @DisplayName("auto-create disabled validates the audit table before journal readiness")
    void autoCreateDisabledValidatesAuditBeforeJournal() throws Exception {
        FeatureFlag.enable(Features.JOURNAL_EVENTS);
        context = setupTest(SqlDialect.SQLITE, "sqlite");
        SimpleContext baseContext = new SimpleContext();
        baseContext.addDependency(RunnerId.generate());
        baseContext.addDependency(new CommunityConfiguration());
        SqlTargetSystem targetSystem = new SqlTargetSystem("sql", context.dataSource);
        targetSystem.initialize(baseContext);

        SqlAuditStore auditStore = SqlAuditStore.from(targetSystem).withAutoCreate(false);

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> auditStore.initialize(baseContext));

        assertTrue(exception.getMessage().toLowerCase().contains("audit"),
                "audit readiness must fail before journal readiness");
    }

    @Test
    @DisplayName("auto-create disabled validates the lock table during store initialization")
    void autoCreateDisabledValidatesLockDuringStoreInitialization() throws Exception {
        context = setupTest(SqlDialect.SQLITE, "sqlite");
        SimpleContext baseContext = new SimpleContext();
        baseContext.addDependency(RunnerId.generate());
        baseContext.addDependency(new CommunityConfiguration());
        SqlTargetSystem targetSystem = new SqlTargetSystem("sql", context.dataSource);
        targetSystem.initialize(baseContext);
        SqlAuditStore.from(targetSystem).initialize(baseContext);
        try (Connection connection = context.dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE flamingockLock");
        }

        SqlAuditStore auditStore = SqlAuditStore.from(targetSystem).withAutoCreate(false);

        RuntimeException exception = assertThrows(RuntimeException.class, () -> auditStore.initialize(baseContext));

        assertTrue(exception.getMessage().toLowerCase().contains("lock"),
                "lock readiness must fail after audit readiness succeeds");
    }

    @Test
    @DisplayName("a stage snapshots the journal flag once and uses the captured value")
    void stageSnapshotsJournalFlagOnce() throws Exception {
        context = setupTest(SqlDialect.SQLITE, "sqlite");
        SimpleContext baseContext = new SimpleContext();
        baseContext.addDependency(RunnerId.generate());
        baseContext.addDependency(new CommunityConfiguration());
        SqlTargetSystem targetSystem = new SqlTargetSystem("sql", context.dataSource);
        targetSystem.initialize(baseContext);
        SqlAuditStore auditStore = SqlAuditStore.from(targetSystem);
        auditStore.initialize(baseContext);

        AtomicInteger flagReads = new AtomicInteger();
        try (MockedStatic<FeatureFlag> flags = org.mockito.Mockito.mockStatic(FeatureFlag.class)) {
            flags.when(() -> FeatureFlag.isEnabled(Features.JOURNAL_EVENTS, false))
                    .thenAnswer(invocation -> flagReads.getAndIncrement() == 0);

            CommunityAuditPersistence persistence = auditStore.getPersistenceFactory().get("captured-stage");
            persistence.writeEntry(auditEntry("captured-flag", AuditEntry.Status.APPLIED));

            assertEquals(1, countRows("flamingockJournalEvents"));
            assertEquals(1, flagReads.get());
            flags.verify(() -> FeatureFlag.isEnabled(Features.JOURNAL_EVENTS, false), org.mockito.Mockito.times(1));
        }
    }

    @Test
    @DisplayName("a Journal flag lookup failure falls back to disabled without touching Journal storage")
    void flagLookupFailureFallsBackToDisabledJournal() throws Exception {
        context = setupTest(SqlDialect.SQLITE, "sqlite");
        SimpleContext baseContext = new SimpleContext();
        baseContext.addDependency(RunnerId.generate());
        baseContext.addDependency(new CommunityConfiguration());
        SqlTargetSystem targetSystem = new SqlTargetSystem("sql", context.dataSource);
        targetSystem.initialize(baseContext);
        SqlAuditStore auditStore = SqlAuditStore.from(targetSystem);
        auditStore.initialize(baseContext);

        try (MockedStatic<FeatureFlag> flags = org.mockito.Mockito.mockStatic(FeatureFlag.class)) {
            flags.when(() -> FeatureFlag.isEnabled(Features.JOURNAL_EVENTS, false))
                    .thenThrow(new RuntimeException("flag lookup failed"));

            CommunityAuditPersistence persistence = auditStore.getPersistenceFactory().get("fallback-stage");
            persistence.writeEntry(auditEntry("fallback-change", AuditEntry.Status.APPLIED));

            assertEquals(1, auditStore.getAuditReader().getAuditHistory().size());
            assertFalse(tableExists("flamingockJournalEvents"));
            flags.verify(() -> FeatureFlag.isEnabled(Features.JOURNAL_EVENTS, false), org.mockito.Mockito.times(1));
        }
    }

    @Test
    @DisplayName("repeated stage initialization validates existing resources without duplicate DDL")
    void repeatedStageInitializationIsIdempotent() throws Exception {
        FeatureFlag.enable(Features.JOURNAL_EVENTS);
        context = setupTest(SqlDialect.SQLITE, "sqlite");
        SimpleContext baseContext = new SimpleContext();
        baseContext.addDependency(RunnerId.generate());
        baseContext.addDependency(new CommunityConfiguration());
        SqlTargetSystem targetSystem = new SqlTargetSystem("sql", context.dataSource);
        targetSystem.initialize(baseContext);
        SqlAuditStore auditStore = SqlAuditStore.from(targetSystem);
        auditStore.initialize(baseContext);

        auditStore.getPersistenceFactory().get("repeatable-stage");
        auditStore.getPersistenceFactory().get("repeatable-stage");

        assertEquals(0, countRows("flamingockJournalEvents"));
    }

    @Test
    @DisplayName("the stage factory does not reinitialize store-owned audit readiness")
    void stageFactoryDoesNotReinitializeAuditReadiness() throws Exception {
        FeatureFlag.enable(Features.JOURNAL_EVENTS);
        context = setupTest(SqlDialect.SQLITE, "sqlite");
        SimpleContext baseContext = new SimpleContext();
        baseContext.addDependency(RunnerId.generate());
        baseContext.addDependency(new CommunityConfiguration());
        SqlTargetSystem targetSystem = new SqlTargetSystem("sql", context.dataSource);
        targetSystem.initialize(baseContext);
        SqlAuditStore auditStore = SqlAuditStore.from(targetSystem);
        auditStore.initialize(baseContext);

        try (Connection connection = context.dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE flamingockAuditLog");
        }

        assertNotNull(auditStore.getPersistenceFactory().get("factory-boundary"));
        assertFalse(tableExists("flamingockAuditLog"));
        assertTrue(tableExists("flamingockJournalEvents"));
    }

    private int countRows(String tableName) throws SQLException {
        try (Connection connection = context.dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM " + tableName)) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }

    private boolean tableExists(String tableName) throws SQLException {
        try (Connection connection = context.dataSource.getConnection();
             ResultSet resultSet = connection.getMetaData().getTables(null, null, null, new String[]{"TABLE"})) {
            while (resultSet.next()) {
                if (tableName.equalsIgnoreCase(resultSet.getString("TABLE_NAME"))) {
                    return true;
                }
            }
            return false;
        }
    }

    private static AuditEntry auditEntry(String changeId, AuditEntry.Status status) {
        return AuditEntryTestFactory.createTestAuditEntry(changeId, status, AuditTxType.NON_TX, (Class<?>) null);
    }

    private void verifyDataState(TestContext context, Boolean partial) throws SQLException {
        try (Connection conn = context.dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT name FROM test_table WHERE id = ?")) {
            ps.setString(1, "test-client-Federico");
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("Federico", rs.getString("name"));
            }
        }

        try (Connection conn = context.dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT name FROM test_table WHERE id = ?")) {
            ps.setString(1, "test-client-Jorge");
            try (ResultSet rs = ps.executeQuery()) {
                if (partial) {
                    assertFalse(rs.next());
                } else {
                    assertTrue(rs.next());
                    assertEquals("Jorge", rs.getString("name"));
                }
            }
        }
    }
}
