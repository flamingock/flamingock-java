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
package io.flamingock.targetsystem.sql;

import io.flamingock.internal.common.sql.AbstractSqlDialectHelper;
import io.flamingock.internal.common.sql.SqlDialect;

import javax.sql.DataSource;

public final class SqlAuditMarkerDialectHelper extends AbstractSqlDialectHelper {

    public SqlAuditMarkerDialectHelper(DataSource dataSource) {
        super(dataSource);
    }

    public SqlAuditMarkerDialectHelper(SqlDialect dialect) {
        super(dialect);
    }

    public String getListAllSqlString(String tableName) {
        return String.format("SELECT task_id, operation FROM %s", tableName);
    }

    public String getClearMarkSqlString(String tableName) {
        return String.format("DELETE FROM %s WHERE task_id = ?", tableName);
    }

    public String getMarkSqlString(String tableName) {
        switch (sqlDialect) {
            case MYSQL:
            case MARIADB:
                return String.format(
                    "INSERT INTO %s (task_id, operation) VALUES (?, ?) ON DUPLICATE KEY UPDATE operation = VALUES(operation)",
                    tableName);
            case POSTGRESQL:
                return String.format(
                    "INSERT INTO %s (task_id, operation) VALUES (?, ?) ON CONFLICT (task_id) DO UPDATE SET operation = EXCLUDED.operation",
                    tableName);
            case SQLITE:
                return String.format(
                    "INSERT OR REPLACE INTO %s (task_id, operation) VALUES (?, ?)",
                    tableName);
            case SQLSERVER:
            case SYBASE:
                return String.format(
                    "MERGE INTO %s AS target USING (SELECT ? AS task_id, ? AS operation) AS source ON (target.task_id = source.task_id) " +
                        "WHEN MATCHED THEN UPDATE SET operation = source.operation WHEN NOT MATCHED THEN INSERT (task_id, operation) VALUES (source.task_id, source.operation);",
                    tableName);
            case ORACLE:
                return String.format(
                    "MERGE INTO %s t USING (SELECT ? AS task_id, ? AS operation FROM dual) s ON (t.task_id = s.task_id) " +
                        "WHEN MATCHED THEN UPDATE SET t.operation = s.operation WHEN NOT MATCHED THEN INSERT (task_id, operation) VALUES (s.task_id, s.operation)",
                    tableName);
            case DB2:
                return String.format(
                    "MERGE INTO %s AS t USING (SELECT ? AS task_id, ? AS operation FROM SYSIBM.SYSDUMMY1) AS s ON (t.task_id = s.task_id) " +
                        "WHEN MATCHED THEN UPDATE SET operation = s.operation WHEN NOT MATCHED THEN INSERT (task_id, operation) VALUES (s.task_id, s.operation)",
                    tableName, tableName);
            case FIREBIRD:
                return String.format(
                    "UPDATE OR INSERT INTO %s (task_id, operation) VALUES (?, ?) MATCHING (task_id)",
                    tableName);
            case H2:
                return String.format(
                    "MERGE INTO %s (task_id, operation) KEY (task_id) VALUES (?, ?)",
                    tableName);
            case HSQLDB:
                return String.format(
                    "MERGE INTO %s AS t USING (VALUES(?,?)) AS s(task_id,operation) ON t.task_id = s.task_id " +
                        "WHEN MATCHED THEN UPDATE SET t.operation = s.operation WHEN NOT MATCHED THEN INSERT (task_id, operation) VALUES (s.task_id, s.operation)",
                    tableName);
            case INFORMIX:
                return String.format(
                    "INSERT INTO %s (task_id, operation) VALUES (?, ?) ON DUPLICATE KEY UPDATE operation = ?",
                    tableName);
            default:
                throw new UnsupportedOperationException("Dialect not supported for upsert: " + sqlDialect.name());
        }
    }

    public String getCreateTableSqlString(String tableName) {
        switch (sqlDialect) {
            case MYSQL:
            case MARIADB:
            case POSTGRESQL:
            case SQLITE:
            case H2:
            case HSQLDB:
            case SQLSERVER:
            case SYBASE:
            case FIREBIRD:
            case INFORMIX:
                return String.format(
                    "CREATE TABLE %s (task_id VARCHAR(255) PRIMARY KEY, operation VARCHAR(50) NOT NULL)",
                    tableName);
            case ORACLE:
                return String.format(
                    "CREATE TABLE %s (task_id VARCHAR2(255) PRIMARY KEY, operation VARCHAR2(50) NOT NULL)",
                    tableName);
            case DB2:
                return String.format(
                    "CREATE TABLE %s (task_id VARCHAR(255) NOT NULL PRIMARY KEY, operation VARCHAR(50) NOT NULL)",
                    tableName);
            default:
                throw new UnsupportedOperationException("Dialect not supported for CREATE TABLE: " + sqlDialect.name());
        }
    }
}
