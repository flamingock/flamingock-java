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
package io.flamingock.community.sql.internal;

import io.flamingock.internal.common.sql.AbstractSqlDialectHelper;
import io.flamingock.internal.common.sql.SqlDialect;

import javax.sql.DataSource;

public final class SqlAuditorDialectHelper extends AbstractSqlDialectHelper {

    public SqlAuditorDialectHelper(DataSource dataSource) {
        super(dataSource);
    }

    public SqlAuditorDialectHelper(SqlDialect dialect) {
        super(dialect);
    }

    private static final String COMMON_COLUMNS =
            "execution_id VARCHAR(255), " +
                    "stage_id VARCHAR(255), " +
                    "task_id VARCHAR(255), " +
                    "author VARCHAR(255), " +
                    "created_at %s, " +
                    "state VARCHAR(255), " +
                    "class_name VARCHAR(255), " +
                    "method_name VARCHAR(255), " +
                    "metadata %s, " +
                    "execution_millis BIGINT, " +
                    "execution_hostname VARCHAR(255), " +
                    "error_trace %s, " +
                    "type VARCHAR(50), " +
                    "tx_type VARCHAR(50), " +
                    "target_system_id VARCHAR(255), " +
                    "order_col VARCHAR(50), " +
                    "recovery_strategy VARCHAR(50), " +
                    "transaction_flag %s, " +
                    "system_change %s";

    private String getCreatedAtType() {
        switch (sqlDialect) {
            case SQLSERVER:
            case SYBASE:
                return "DATETIME DEFAULT GETDATE()";
            case INFORMIX:
                return "DATETIME YEAR TO SECOND DEFAULT CURRENT YEAR TO SECOND";
            case ORACLE:
            case POSTGRESQL:
            case MYSQL:
            case MARIADB:
            case SQLITE:
            case H2:
            case HSQLDB:
            case DERBY:
            case FIREBIRD:
                return "TIMESTAMP DEFAULT CURRENT_TIMESTAMP";
            case DB2:
                return "TIMESTAMP DEFAULT CURRENT TIMESTAMP";
            default:
                return "TIMESTAMP";
        }
    }

    private String getMetadataType() {
        switch (sqlDialect) {
            case ORACLE:
            case DB2:
                return "CLOB";
            case FIREBIRD:
                return "BLOB SUB_TYPE TEXT";
            default:
                return "TEXT";
        }
    }

    private String getErrorTraceType() {
        switch (sqlDialect) {
            case ORACLE:
            case DB2:
                return "CLOB";
            case FIREBIRD:
                return "BLOB SUB_TYPE TEXT";
            default:
                return "TEXT";
        }
    }

    private String getBooleanType() {
        switch (sqlDialect) {
            case SQLSERVER:
            case SYBASE:
                return "BIT";
            case ORACLE:
            case DB2:
            case FIREBIRD:
            case INFORMIX:
                return "SMALLINT";
            default:
                return "BOOLEAN";
        }
    }

    public String getCreateTableSqlString(String tableName) {
        String columns = String.format(COMMON_COLUMNS,
                getCreatedAtType(),
                getMetadataType(),
                getErrorTraceType(),
                getBooleanType(),
                getBooleanType());

        switch (sqlDialect) {
            case MYSQL:
            case MARIADB:
                return String.format(
                        "CREATE TABLE IF NOT EXISTS %s (" +
                                "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                                columns +
                                ")", tableName);
            case POSTGRESQL:
                return String.format(
                        "CREATE TABLE IF NOT EXISTS %s (" +
                                "id SERIAL PRIMARY KEY, " +
                                columns +
                                ")", tableName);
            case SQLITE:
            case H2:
            case HSQLDB:
            case DERBY:
                return String.format(
                        "CREATE TABLE IF NOT EXISTS %s (" +
                                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                                columns +
                                ")", tableName);
            case SQLSERVER:
            case SYBASE:
                return String.format(
                        "IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='%s' AND xtype='U') " +
                                "CREATE TABLE %s (" +
                                "id BIGINT IDENTITY(1,1) PRIMARY KEY, " +
                                columns +
                                ")", tableName, tableName);
            case ORACLE:
                return String.format(
                        "BEGIN EXECUTE IMMEDIATE 'CREATE TABLE %s (" +
                                "id NUMBER GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, " +
                                columns +
                                ")'; EXCEPTION WHEN OTHERS THEN IF SQLCODE != -955 THEN RAISE; END IF; END;", tableName);
            case DB2:
                return String.format(
                        "CREATE TABLE %s (" +
                                "id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, " +
                                columns +
                                ")", tableName);
            case FIREBIRD:
                return String.format(
                        "CREATE TABLE %s (" +
                                "id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, " +
                                columns +
                                ")", tableName);
            case INFORMIX:
                return String.format(
                        "CREATE TABLE %s (" +
                                "id SERIAL PRIMARY KEY, " +
                                columns +
                                ")", tableName);
            default:
                throw new UnsupportedOperationException("Dialect not supported for CREATE TABLE: " + sqlDialect.name());
        }
    }

    public String getInsertSqlString(String tableName) {
        return String.format(
                "INSERT INTO %s (" +
                        "execution_id, stage_id, task_id, author, created_at, state, class_name, method_name, metadata, " +
                        "execution_millis, execution_hostname, error_trace, type, tx_type, target_system_id, order_col, " +
                        "recovery_strategy, transaction_flag, system_change" +
                        ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                tableName);
    }

    public String getSelectHistorySqlString(String tableName) {
        return String.format(
                "SELECT execution_id, stage_id, task_id, author, created_at, state, class_name, method_name, metadata, " +
                        "execution_millis, execution_hostname, error_trace, type, tx_type, target_system_id, order_col, " +
                        "recovery_strategy, transaction_flag, system_change " +
                        "FROM %s ORDER BY created_at DESC",
                tableName);
    }
}
