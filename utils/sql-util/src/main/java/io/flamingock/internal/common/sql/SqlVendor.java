/*
  * Copyright 2025 Flamingock (https://oss.flamingock.io)
 \*
  * Licensed under the Apache License, Version 2.0 (the "License");
  * you may not use this file except in compliance with the License.
  * You may obtain a copy of the License at
 \*
  * http://www.apache.org/licenses/LICENSE-2.0
 \*
  * Unless required by applicable law or agreed to in writing, software
  * distributed under the License is distributed on an "AS IS" BASIS,
  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  * See the License for the specific language governing permissions and
  * limitations under the License.
 */

package io.flamingock.internal.common.sql;

public enum SqlVendor {
    MYSQL,
    MARIADB,
    POSTGRESQL,
    SQLITE,
    H2,
    HSQLDB,
    DERBY,
    SQLSERVER,
    SYBASE,
    FIREBIRD,
    INFORMIX,
    ORACLE,
    DB2,
    UNKNOWN;

    public static SqlVendor fromString(String vendor) {
        if (vendor == null) {
            return UNKNOWN;
        }
        String v = vendor.toLowerCase();
        if (v.contains("mysql")) {
            return MYSQL;
        } else if (v.contains("mariadb")) {
            return MARIADB;
        } else if (v.contains("postgresql")) {
            return POSTGRESQL;
        } else if (v.contains("sqlite")) {
            return SQLITE;
        } else if (v.contains("h2")) {
            return H2;
        } else if (v.contains("hsqldb")) {
            return HSQLDB;
        } else if (v.contains("derby")) {
            return DERBY;
        } else if (v.contains("sql server")) {
            return SQLSERVER;
        } else if (v.contains("sybase")) {
            return SYBASE;
        } else if (v.contains("firebird")) {
            return FIREBIRD;
        } else if (v.contains("informix")) {
            return INFORMIX;
        } else if (v.contains("oracle")) {
            return ORACLE;
        } else if (v.contains("db2")) {
            return DB2;
        } else {
            return UNKNOWN;
        }
    }
}
