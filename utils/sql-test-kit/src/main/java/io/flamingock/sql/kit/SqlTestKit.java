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
package io.flamingock.sql.kit;

import io.flamingock.core.kit.AbstractTestKit;
import io.flamingock.core.kit.audit.AuditStorage;
import io.flamingock.core.kit.lock.LockStorage;
import io.flamingock.internal.common.sql.SqlDialect;
import io.flamingock.internal.core.external.store.CommunityAuditStore;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

public class SqlTestKit extends AbstractTestKit {

    private final DataSource dataSource;
    private final SqlDialectHelper dialectHelper;

    public SqlTestKit(AuditStorage auditStorage, LockStorage lockStorage, CommunityAuditStore auditStore, DataSource dataSource) throws SQLException {
        super(auditStorage, lockStorage, auditStore);
        this.dataSource = dataSource;
        try (Connection conn = dataSource.getConnection()) {
            this.dialectHelper = new SqlDialectHelper(conn);
        }
    }

    @Override
    public void cleanUp() {
        try {
            try (Connection connection = dataSource.getConnection()) {
                if (dialectHelper.getSqlDialect() == SqlDialect.H2) {
                    try (Statement stmt = connection.createStatement()) {
                        stmt.executeUpdate("DROP ALL OBJECTS");
                    }
                    return;
                }
                List<String> tables = dialectHelper.getUserTables(connection);

                if (tables.isEmpty()) {
                    return;
                }

                dialectHelper.disableForeignKeyChecks(connection);
                try {
                    for (String tableName : tables) {
                        try (Statement stmt = connection.createStatement()) {
                            stmt.executeUpdate(dialectHelper.getDropTableSql(tableName));
                        } catch (SQLException e) {
                            // Para Sybase, ignorar si la tabla ya no existe
                            if (dialectHelper.getSqlDialect() != SqlDialect.SYBASE) {
                                throw e;
                            }
                        }
                    }
                } finally {
                    dialectHelper.enableForeignKeyChecks(connection);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Create a new SqlTestKit with client and database
     */
    public static SqlTestKit create(CommunityAuditStore auditStore, DataSource dataSource) throws SQLException {
        SqlAuditStorage auditStorage = new SqlAuditStorage(dataSource);
        SqlLockStorage lockStorage = new SqlLockStorage(dataSource);
        return new SqlTestKit(auditStorage, lockStorage, auditStore, dataSource);
    }
}
