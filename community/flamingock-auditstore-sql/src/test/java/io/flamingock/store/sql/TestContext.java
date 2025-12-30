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
import java.sql.SQLException;

public class TestContext {
    final DataSource dataSource;
    final JdbcDatabaseContainer<?> container;
    final SqlDialect dialect;

    TestContext(DataSource dataSource, JdbcDatabaseContainer<?> container, SqlDialect dialect) {
        this.dataSource = dataSource;
        this.container = container;
        this.dialect = dialect;
    }

    public void cleanup() throws SQLException {
        if (dataSource instanceof HikariDataSource) {
            HikariDataSource hikariDS = (HikariDataSource) dataSource;
            hikariDS.getHikariPoolMXBean().softEvictConnections();
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            hikariDS.close();
        }
    }
}