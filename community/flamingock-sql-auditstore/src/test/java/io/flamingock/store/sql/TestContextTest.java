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
import io.flamingock.internal.common.sql.SqlDialect;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.JdbcDatabaseContainer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TestContextTest {

    @Test
    void cleanupClosesOwnedResourcesAndDeletesSQLiteArtifactsIdempotently() throws Exception {
        Path databaseFile = Files.createTempFile("sql-test-context-", ".db").toAbsolutePath();
        Path walFile = databaseFile.resolveSibling(databaseFile.getFileName() + "-wal");
        Path shmFile = databaseFile.resolveSibling(databaseFile.getFileName() + "-shm");
        Files.createFile(walFile);
        Files.createFile(shmFile);

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl("jdbc:sqlite:" + databaseFile);
        hikariConfig.setMaximumPoolSize(1);
        HikariDataSource dataSource = new HikariDataSource(hikariConfig);
        try (Connection ignored = dataSource.getConnection()) {
            // Open the owned pool before cleanup verifies that it closes the data source.
        }

        JdbcDatabaseContainer<?> container = mock(JdbcDatabaseContainer.class);
        when(container.isRunning()).thenReturn(true);
        TestContext context = new TestContext(dataSource, container, SqlDialect.SQLITE, databaseFile);

        context.cleanup();
        context.cleanup();

        assertTrue(dataSource.isClosed());
        assertFalse(Files.exists(databaseFile));
        assertFalse(Files.exists(walFile));
        assertFalse(Files.exists(shmFile));
        verify(container).stop();
    }

    @Test
    void cleanupHandlesSQLiteDatabaseWithoutSidecars() throws Exception {
        Path databaseFile = Files.createTempFile("sql-test-context-no-sidecars-", ".db").toAbsolutePath();
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl("jdbc:sqlite:" + databaseFile);
        hikariConfig.setMaximumPoolSize(1);
        HikariDataSource dataSource = new HikariDataSource(hikariConfig);
        try (Connection ignored = dataSource.getConnection()) {
            // Open the database so cleanup also closes a real owned connection pool.
        }

        new TestContext(dataSource, null, SqlDialect.SQLITE, databaseFile).cleanup();

        assertFalse(Files.exists(databaseFile));
    }
}
