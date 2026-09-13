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

import com.zaxxer.hikari.HikariDataSource;
import io.flamingock.internal.common.sql.SqlDialect;
import org.testcontainers.containers.JdbcDatabaseContainer;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;

public class TestContext {
    final DataSource dataSource;
    final JdbcDatabaseContainer<?> container;
    final SqlDialect dialect;
    final Path sqliteDatabase;
    private boolean cleanedUp;

    TestContext(DataSource dataSource, JdbcDatabaseContainer<?> container, SqlDialect dialect) {
        this(dataSource, container, dialect, null);
    }

    TestContext(DataSource dataSource,
                JdbcDatabaseContainer<?> container,
                SqlDialect dialect,
                Path sqliteDatabase) {
        this.dataSource = dataSource;
        this.container = container;
        this.dialect = dialect;
        this.sqliteDatabase = sqliteDatabase;
    }

    public synchronized void cleanup() throws SQLException {
        if (cleanedUp) {
            return;
        }

        SQLException cleanupFailure = null;
        try {
            closeDataSource();
        } catch (SQLException exception) {
            cleanupFailure = exception;
        }

        try {
            stopContainer();
        } catch (RuntimeException exception) {
            cleanupFailure = appendFailure(cleanupFailure,
                    new SQLException("Could not stop the SQL test container", exception));
        }

        try {
            deleteSQLiteArtifacts();
        } catch (IOException exception) {
            cleanupFailure = appendFailure(cleanupFailure,
                    new SQLException("Could not delete the SQLite test database", exception));
        }

        cleanedUp = true;
        if (cleanupFailure != null) {
            throw cleanupFailure;
        }
    }

    private void closeDataSource() throws SQLException {
        if (dataSource instanceof HikariDataSource) {
            HikariDataSource hikariDataSource = (HikariDataSource) dataSource;
            if (hikariDataSource.getHikariPoolMXBean() != null) {
                hikariDataSource.getHikariPoolMXBean().softEvictConnections();
                try {
                    Thread.sleep(100);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            }
            hikariDataSource.close();
        } else if (dataSource instanceof AutoCloseable) {
            try {
                ((AutoCloseable) dataSource).close();
            } catch (Exception exception) {
                throw new SQLException("Could not close the SQL test data source", exception);
            }
        }
    }

    private void stopContainer() {
        if (container != null && container.isRunning()) {
            container.stop();
        }
    }

    private void deleteSQLiteArtifacts() throws IOException {
        if (sqliteDatabase == null) {
            return;
        }

        Files.deleteIfExists(sqliteDatabase);
        Files.deleteIfExists(sqliteDatabase.resolveSibling(sqliteDatabase.getFileName() + "-wal"));
        Files.deleteIfExists(sqliteDatabase.resolveSibling(sqliteDatabase.getFileName() + "-shm"));
    }

    private static SQLException appendFailure(SQLException current, SQLException next) {
        if (current == null) {
            return next;
        }
        current.addSuppressed(next);
        return current;
    }
}
