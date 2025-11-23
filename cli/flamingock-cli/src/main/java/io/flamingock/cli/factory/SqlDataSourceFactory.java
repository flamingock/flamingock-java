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
package io.flamingock.cli.factory;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.flamingock.cli.config.DatabaseConfig;
import io.flamingock.internal.common.sql.SqlDialect;
import org.sqlite.SQLiteDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.sql.Statement;

public class SqlDataSourceFactory {

    public static DataSource createSqlDataSource(DatabaseConfig.SqlConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("SQL configuration is required");
        }

        if (config.getEndpoint() == null) {
            throw new IllegalArgumentException("JdbcUrl is required");
        }

        if (config.getUsername() == null) {
            throw new IllegalArgumentException("Database username is required");
        }

        if (config.getPassword() == null) {
            throw new IllegalArgumentException("Database password is required");
        }

        if (config.getSqlDialect() == null) {
            throw new IllegalArgumentException("Sql dialect is required");
        }

        try {
            DataSource sqlDatasource;

            if (config.getSqlDialect().equals(SqlDialect.SQLITE)) {
                SQLiteDataSource sqliteDatasource = new SQLiteDataSource();
                sqliteDatasource.setUrl(config.getEndpoint());

                try (Connection conn = sqliteDatasource.getConnection();
                     Statement stmt = conn.createStatement()) {
                    stmt.execute("PRAGMA journal_mode=WAL;");
                    stmt.execute("PRAGMA busy_timeout=5000;");
                }

                sqlDatasource = sqliteDatasource;
            } else {
                HikariConfig datasourceConfig = new HikariConfig();
                datasourceConfig.setDriverClassName(config.getDriverClassName());

                if (config.getSqlDialect().equals(SqlDialect.H2)) {
                    datasourceConfig.setJdbcUrl("jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1");
                    datasourceConfig.setUsername("sa");
                    datasourceConfig.setPassword("");
                } else {
                    datasourceConfig.setJdbcUrl(config.getEndpoint());
                    datasourceConfig.setUsername(config.getUsername());
                    datasourceConfig.setPassword(config.getPassword());
                }

                if (config.getSqlDialect() == SqlDialect.INFORMIX) {
                    datasourceConfig.setMaximumPoolSize(1);
                    datasourceConfig.setMinimumIdle(0);
                    datasourceConfig.setConnectionTimeout(30000);
                    datasourceConfig.setIdleTimeout(600000);
                    datasourceConfig.setMaxLifetime(1800000);
                    datasourceConfig.setLeakDetectionThreshold(0);
                    datasourceConfig.setValidationTimeout(5000);
                    datasourceConfig.setConnectionTestQuery("SELECT 1 FROM systables WHERE tabid=1");
                    datasourceConfig.setInitializationFailTimeout(-1);
                    datasourceConfig.setAutoCommit(true);
                }

                sqlDatasource = new HikariDataSource(datasourceConfig);
            }

            // Test the connection by listing tables
             try (Connection conn = sqlDatasource.getConnection()) {
                 DatabaseMetaData metaData = conn.getMetaData();
                 metaData.getTables(null, null, "%", null);
             } catch (SQLException e) {
                 throw new RuntimeException("Failed to validate SQL DataSource connection: " + e.getMessage(), e);
             }

            return sqlDatasource;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create SQL DataSource: " + e.getMessage(), e);
        }
    }
}
