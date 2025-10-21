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

    public String getCreateTableSqlString(String tableName) {
        switch (sqlDialect) {
            case MYSQL:
            case MARIADB:
            case SQLITE:
            case H2:
            case HSQLDB:
            case DERBY:
            case FIREBIRD:
            case INFORMIX:
                return String.format(
                        "CREATE TABLE IF NOT EXISTS %s (" +
                                "id %s PRIMARY KEY, " +
                                "execution_id VARCHAR(255), " +
                                "stage_id VARCHAR(255), " +
                                "task_id VARCHAR(255), " +
                                "author VARCHAR(255), " +
                                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                                "state VARCHAR(255), " +
                                "class_name VARCHAR(255), " +
                                "method_name VARCHAR(255), " +
                                "metadata %s, " +
                                "execution_millis %s, " +
                                "execution_hostname VARCHAR(255), " +
                                "error_trace %s, " +
                                "type VARCHAR(50), " +
                                "tx_type VARCHAR(50), " +
                                "target_system_id VARCHAR(255), " +
                                "order_col VARCHAR(50), " +
                                "recovery_strategy VARCHAR(50), " +
                                "transaction_flag %s, " +
                                "system_change %s" +
                                ")", tableName, getAutoIncrementType(), getClobType(), getBigIntType(), getClobType(), getBooleanType(), getBooleanType());
            case POSTGRESQL:
                return String.format(
                        "CREATE TABLE IF NOT EXISTS %s (" +
                                "id SERIAL PRIMARY KEY," +
                                "execution_id VARCHAR(255)," +
                                "stage_id VARCHAR(255)," +
                                "task_id VARCHAR(255)," +
                                "author VARCHAR(255)," +
                                "created_at TIMESTAMP," +
                                "state VARCHAR(32)," +
                                "class_name VARCHAR(255)," +
                                "method_name VARCHAR(255)," +
                                "metadata TEXT," +
                                "execution_millis BIGINT," +
                                "execution_hostname VARCHAR(255)," +
                                "error_trace TEXT," +
                                "type VARCHAR(32)," +
                                "tx_type VARCHAR(32)," +
                                "target_system_id VARCHAR(255)," +
                                "order_col VARCHAR(255)," +
                                "recovery_strategy VARCHAR(32)," +
                                "transaction_flag BOOLEAN," +
                                "system_change BOOLEAN" +
                                ")", tableName);

            case SQLSERVER:
            case SYBASE:
                return String.format(
                        "IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='%s' AND xtype='U') " +
                                "CREATE TABLE %s (" +
                                "id %s PRIMARY KEY, " +
                                "execution_id VARCHAR(255), " +
                                "stage_id VARCHAR(255), " +
                                "task_id VARCHAR(255), " +
                                "author VARCHAR(255), " +
                                "created_at DATETIME DEFAULT GETDATE(), " +
                                "state VARCHAR(255), " +
                                "class_name VARCHAR(255), " +
                                "method_name VARCHAR(255), " +
                                "metadata %s, " +
                                "execution_millis %s, " +
                                "execution_hostname VARCHAR(255), " +
                                "error_trace %s, " +
                                "type VARCHAR(50), " +
                                "tx_type VARCHAR(50), " +
                                "target_system_id VARCHAR(255), " +
                                "order_col VARCHAR(50), " +
                                "recovery_strategy VARCHAR(50), " +
                                "transaction_flag %s, " +
                                "system_change %s" +
                                ")", tableName, tableName, getAutoIncrementType(), getClobType(), getBigIntType(), getClobType(), getBooleanType(), getBooleanType());
            case ORACLE:
                return String.format(
                        "BEGIN EXECUTE IMMEDIATE 'CREATE TABLE %s (" +
                                "id %s PRIMARY KEY, " +
                                "execution_id VARCHAR2(255), " +
                                "stage_id VARCHAR2(255), " +
                                "task_id VARCHAR2(255), " +
                                "author VARCHAR2(255), " +
                                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                                "state VARCHAR2(255), " +
                                "class_name VARCHAR2(255), " +
                                "method_name VARCHAR2(255), " +
                                "metadata %s, " +
                                "execution_millis %s, " +
                                "execution_hostname VARCHAR2(255), " +
                                "error_trace %s, " +
                                "type VARCHAR2(50), " +
                                "tx_type VARCHAR2(50), " +
                                "target_system_id VARCHAR2(255), " +
                                "order_col VARCHAR2(50), " +
                                "recovery_strategy VARCHAR2(50), " +
                                "transaction_flag %s, " +
                                "system_change %s" +
                                ")'; EXCEPTION WHEN OTHERS THEN IF SQLCODE != -955 THEN RAISE; END IF; END;", tableName, getAutoIncrementType(), getClobType(), getBigIntType(), getClobType(), getBooleanType(), getBooleanType());
            case DB2:
                return String.format(
                        "CREATE TABLE %s (" +
                                "id %s PRIMARY KEY, " +
                                "execution_id VARCHAR(255), " +
                                "stage_id VARCHAR(255), " +
                                "task_id VARCHAR(255), " +
                                "author VARCHAR(255), " +
                                "created_at TIMESTAMP DEFAULT CURRENT TIMESTAMP, " +
                                "state VARCHAR(255), " +
                                "class_name VARCHAR(255), " +
                                "method_name VARCHAR(255), " +
                                "metadata %s, " +
                                "execution_millis %s, " +
                                "execution_hostname VARCHAR(255), " +
                                "error_trace %s, " +
                                "type VARCHAR(50), " +
                                "tx_type VARCHAR(50), " +
                                "target_system_id VARCHAR(255), " +
                                "order_col VARCHAR(50), " +
                                "recovery_strategy VARCHAR(50), " +
                                "transaction_flag %s, " +
                                "system_change %s" +
                                ")", tableName, getAutoIncrementType(), getClobType(), getBigIntType(), getClobType(), getBooleanType(), getBooleanType());
            default:
                throw new UnsupportedOperationException("Dialect not supported for CREATE TABLE: " + sqlDialect.name());
        }
    }

    public String getInsertSqlString(String tableName) {
        return String.format(
                "INSERT INTO %s (execution_id, stage_id, task_id, author, created_at, state, class_name, method_name, metadata, execution_millis, execution_hostname, error_trace, type, tx_type, target_system_id, order_col, recovery_strategy, transaction_flag, system_change) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", tableName);
    }

    public String getSelectHistorySqlString(String tableName) {
        return String.format("SELECT * FROM %s ORDER BY id ASC", tableName);
    }

    private String getAutoIncrementType() {
        switch (sqlDialect) {
            case POSTGRESQL:
                return "BIGSERIAL";
            case SQLITE:
            case H2:
            case HSQLDB:
            case DERBY:
            case DB2:
            case FIREBIRD:
                return "BIGINT GENERATED BY DEFAULT AS IDENTITY";
            case SQLSERVER:
            case SYBASE:
                return "BIGINT IDENTITY(1,1)";
            case ORACLE:
                return "NUMBER GENERATED BY DEFAULT AS IDENTITY";
            case INFORMIX:
                return "SERIAL8";
            case MYSQL:
            case MARIADB:
            default:
                return "BIGINT AUTO_INCREMENT";
        }
    }

    private String getClobType() {
        switch (sqlDialect) {
            case MYSQL:
            case MARIADB:
                return "LONGTEXT";
            case SQLITE:
            case H2:
            case HSQLDB:
            case DERBY:
            case FIREBIRD:
            case INFORMIX:
            case ORACLE:
            case DB2:
                return "CLOB";
            case SQLSERVER:
            case SYBASE:
                return "NVARCHAR(MAX)";
            case POSTGRESQL:
            default:
                return "TEXT";
        }
    }

    private String getBigIntType() {
        switch (sqlDialect) {
            case ORACLE:
                return "NUMBER(19)";
            default:
                return "BIGINT";
        }
    }

    private String getBooleanType() {
        switch (sqlDialect) {
            case MYSQL:
            case MARIADB:
                return "TINYINT(1)";
            case POSTGRESQL:
            case H2:
            case HSQLDB:
            case DERBY:
            case FIREBIRD:
            case INFORMIX:
                return "BOOLEAN";
            case SQLITE:
                return "INTEGER";
            case SQLSERVER:
            case SYBASE:
                return "BIT";
            case ORACLE:
                return "NUMBER(1)";
            default:
                return "SMALLINT";
        }
    }

    public SqlDialect getSqlDialect() {
        return sqlDialect;
    }
}
