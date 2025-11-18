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
package io.flamingock.community.sql;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.testcontainers.containers.*;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.containers.wait.strategy.WaitAllStrategy;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

public final class SharedSqlContainers {

    private static final ConcurrentHashMap<String, JdbcDatabaseContainer<?>> CONTAINERS = new ConcurrentHashMap<>();

    private SharedSqlContainers() { }

    public static JdbcDatabaseContainer<?> getContainer(String dialectName) {
        boolean isCi = System.getenv("CI") != null || System.getenv("GITHUB_ACTIONS") != null;
        return CONTAINERS.computeIfAbsent(dialectName, key -> createContainerInternal(key, isCi));
    }

    private static JdbcDatabaseContainer<?> createContainerInternal(String dialectName, boolean isCi) {
        switch (dialectName) {
            case "mysql": {
                MySQLContainer<?> c = new MySQLContainer<>("mysql:8.0")
                        .withDatabaseName("testdb")
                        .withUsername("testuser")
                        .withPassword("testpass");
                if (!isCi) c.withReuse(true);
                return c;
            }
            case "sqlserver": {
                MSSQLServerContainer<?> c = new MSSQLServerContainer<>("mcr.microsoft.com/mssql/server:2019-latest")
                        .acceptLicense()
                        .withPassword("TestPass123!");
                if (!isCi) c.withReuse(true);
                return c;
            }
            case "oracle": {
                OracleContainer c = new OracleContainer(
                        DockerImageName.parse("gvenzl/oracle-free:23-slim-faststart")
                                .asCompatibleSubstituteFor("gvenzl/oracle-xe")) {
                    @Override
                    public String getDatabaseName() {
                        return "FREEPDB1";
                    }
                }
                        .withPassword("oracle123")
                        .withSharedMemorySize(2147483648L)
                        .withStartupTimeout(Duration.ofMinutes(10))
                        .withStartupAttempts(2)
                        .waitingFor(new WaitAllStrategy()
                                .withStrategy(Wait.forListeningPort())
                                .withStrategy(Wait.forLogMessage(".*DATABASE IS READY TO USE.*\\n", 1))
                        )
                        .withEnv("ORACLE_CHARACTERSET", "AL32UTF8");
                if (!isCi) c.withReuse(true);
                return c;
            }
            case "postgresql": {
                PostgreSQLContainer<?> c = new PostgreSQLContainer<>(DockerImageName.parse("postgres:15"))
                        .withDatabaseName("testdb")
                        .withUsername("test")
                        .withPassword("test");
                if (!isCi) c.withReuse(true);
                return c;
            }
            case "mariadb": {
                MariaDBContainer<?> c = new MariaDBContainer<>("mariadb:11.3.2")
                        .withDatabaseName("testdb")
                        .withUsername("testuser")
                        .withPassword("testpass");
                if (!isCi) c.withReuse(true);
                return c;
            }
            case "informix":
                return new InformixContainer();
            case "firebird": {
                FirebirdJdbcContainer c = new FirebirdJdbcContainer("jacobalberty/firebird:latest");
                c.withEnv("FIREBIRD_DATABASE", "test.fdb")
                        .withEnv("ISC_PASSWORD", "masterkey")
                        .withEnv("FIREBIRD_CHARACTERSET", "UTF8");
                if (!isCi) {
                    c.withReuse(true);
                }
                return c;
            }
             default:
                throw new IllegalArgumentException("Unsupported dialect: " + dialectName);
        }
    }

    public static void stopAll() {
        CONTAINERS.values().forEach(JdbcDatabaseContainer::stop);
        CONTAINERS.clear();
    }

    public static DataSource createDataSource(JdbcDatabaseContainer<?> container) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(container.getJdbcUrl());
        config.setUsername(container.getUsername());
        config.setPassword(container.getPassword());
        config.setDriverClassName(container.getDriverClassName());

        if (container instanceof InformixContainer) {
            config.setMaximumPoolSize(1);
            config.setMinimumIdle(0);
            config.setConnectionTimeout(30000);
            config.setIdleTimeout(600000);
            config.setMaxLifetime(1800000);
            config.setLeakDetectionThreshold(0);
            config.setValidationTimeout(5000);
            config.setConnectionTestQuery("SELECT 1 FROM systables WHERE tabid=1");
            config.setInitializationFailTimeout(-1);
            config.setAutoCommit(true);
        }

        return new HikariDataSource(config);
    }
}
