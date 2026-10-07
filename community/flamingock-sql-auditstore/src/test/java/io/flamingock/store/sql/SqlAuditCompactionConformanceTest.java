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
package io.flamingock.store.sql;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.flamingock.core.kit.audit.compaction.AuditCompactionConformance;
import io.flamingock.core.kit.audit.compaction.AuditStorageCompactionFixture;
import io.flamingock.internal.common.core.feature.Features;
import io.flamingock.internal.common.sql.SqlDialect;
import io.flamingock.internal.core.configuration.community.CommunityConfiguration;
import io.flamingock.internal.core.context.SimpleContext;
import io.flamingock.internal.util.FeatureFlag;
import io.flamingock.internal.util.id.RunnerId;
import io.flamingock.sql.kit.SqlAuditStorage;
import io.flamingock.targetsystem.sql.SqlTargetSystem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.sqlite.SQLiteDataSource;
import org.testcontainers.containers.JdbcDatabaseContainer;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Audit compaction against every runtime SQL dialect: the shared contract, once per dialect.
 * <p>
 * SQL does not rekey — its current-state row and ledger rows share the same key shape, filtered only by
 * {@code change_id} (contrast DynamoDB/Couchbase, which key the two shapes differently and so need a
 * store-specific physical-key assertion on top of the shared suite). So the shared conformance suite is
 * the whole of what needs proving here; there is nothing SQL-specific left to assert on raw rows.
 * <p>
 * One {@code verifyAll()} call per dialect, rather than one {@code @Test} per property, to avoid spinning
 * a container (Oracle in particular) once per property per dialect.
 */
class SqlAuditCompactionConformanceTest {

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

    @AfterEach
    void tearDown() throws SQLException {
        FeatureFlag.remove(Features.JOURNAL_EVENTS);
        if (context != null) {
            context.cleanup();
        }
    }

    @ParameterizedTest
    @MethodSource("dialectProvider")
    @DisplayName("audit compaction satisfies the shared contract")
    void compactionSatisfiesContract(SqlDialect sqlDialect, String dialectName) throws Exception {
        context = setupTest(sqlDialect, dialectName);

        SimpleContext baseContext = new SimpleContext();
        baseContext.addDependency(RunnerId.generate());
        baseContext.addDependency(new CommunityConfiguration());
        SqlTargetSystem targetSystem = new SqlTargetSystem("sql", context.dataSource);
        targetSystem.initialize(baseContext);

        SqlAuditStore auditStore = SqlAuditStore.from(targetSystem);
        auditStore.initialize(baseContext);

        SqlAuditStorage auditStorage = new SqlAuditStorage(context.dataSource);
        AuditCompactionConformance conformance = new AuditCompactionConformance(
                new AuditStorageCompactionFixture(auditStore, auditStorage, "compaction-stage"));

        conformance.verifyAll();
    }

    /**
     * Mirrors {@code SqlAuditStoreTest#setupTest}: H2 and SQLite run in-process, every other dialect in a
     * Testcontainers-backed container. Audit and lock tables are left to the store's own
     * {@code initialize(autoCreate=true)} rather than created here, since this test needs nothing beyond
     * what the production path already provisions.
     */
    private TestContext setupTest(SqlDialect sqlDialect, String dialectName) throws SQLException {
        if ("h2".equals(dialectName)) {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl("jdbc:h2:mem:testdb-compaction-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
            config.setUsername("sa");
            config.setPassword("");
            config.setDriverClassName("org.h2.Driver");
            DataSource dataSource = new HikariDataSource(config);
            return new TestContext(dataSource, null, sqlDialect);
        }

        if ("sqlite".equals(dialectName)) {
            Path databaseFile;
            try {
                databaseFile = Files.createTempFile("flamingock-sql-audit-compaction-", ".db").toAbsolutePath();
            } catch (IOException exception) {
                throw new SQLException("Could not create a temporary SQLite database", exception);
            }

            SQLiteDataSource ds = new SQLiteDataSource();
            ds.setUrl("jdbc:sqlite:" + databaseFile);
            TestContext testContext = new TestContext(ds, null, sqlDialect, databaseFile);

            try (Connection conn = ds.getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.execute("PRAGMA journal_mode=WAL;");
                stmt.execute("PRAGMA busy_timeout=5000;");
            } catch (SQLException exception) {
                testContext.cleanup();
                throw exception;
            }

            return testContext;
        }

        JdbcDatabaseContainer<?> container = SqlAuditTestHelper.createContainer(dialectName);
        container.start();

        DataSource dataSource = null;
        TestContext testContext = null;
        try {
            dataSource = SqlAuditTestHelper.createDataSource(container);
            testContext = new TestContext(dataSource, container, sqlDialect);
            return testContext;
        } catch (RuntimeException exception) {
            TestContext cleanupContext = testContext != null
                    ? testContext
                    : new TestContext(dataSource, container, sqlDialect);
            try {
                cleanupContext.cleanup();
            } catch (SQLException cleanupFailure) {
                exception.addSuppressed(cleanupFailure);
            }
            throw exception;
        }
    }
}
