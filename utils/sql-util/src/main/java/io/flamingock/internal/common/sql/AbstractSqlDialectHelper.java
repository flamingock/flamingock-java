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
package io.flamingock.internal.common.sql;

import java.sql.Connection;
import java.sql.SQLException;

public class AbstractSqlDialectHelper {

    protected final SqlDialect sqlDialect;

    public AbstractSqlDialectHelper(SqlDialect sqlDialect) {
        this.sqlDialect = sqlDialect;
    }

    public AbstractSqlDialectHelper(Connection connection) {
        try {
            sqlDialect = fromDatabaseProductName(connection.getMetaData().getDatabaseProductName());
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to obtain database product name from DataSource", e);
        }
    }

    public SqlDialect getSqlDialect() {
        return sqlDialect;
    }

    private SqlDialect fromDatabaseProductName(String productName) {
        if (productName == null) {
            throw new IllegalArgumentException("Database product name is null; cannot determine SQL dialect.");
        }
        String v = productName.toLowerCase();
        if (v.contains("mysql")) {
            return SqlDialect.MYSQL;
        } else if (v.contains("mariadb")) {
            return SqlDialect.MARIADB;
        } else if (v.contains("postgresql")) {
            return SqlDialect.POSTGRESQL;
        } else if (v.contains("sqlite")) {
            return SqlDialect.SQLITE;
        } else if (v.contains("h2")) {
            return SqlDialect.H2;
        /*} else if (v.contains("hsql")) {
            return SqlDialect.HSQLDB;
         */
        } else if (v.contains("sql server")) {
            return SqlDialect.SQLSERVER;
        } else if (v.contains("sybase") || v.contains("adaptive server")) {
            return SqlDialect.SYBASE;
        } else if (v.contains("firebird")) {
            return SqlDialect.FIREBIRD;
        } else if (v.contains("informix")) {
            return SqlDialect.INFORMIX;
        } else if (v.contains("oracle")) {
            return SqlDialect.ORACLE;
        } else if (v.contains("db2")) {
            return SqlDialect.DB2;
        } else {
            throw new IllegalArgumentException("Unsupported SQL dialect: " + productName);
        }
    }
}
