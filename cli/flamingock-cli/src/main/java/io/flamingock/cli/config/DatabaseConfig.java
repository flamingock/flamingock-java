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
package io.flamingock.cli.config;

import io.flamingock.internal.common.sql.SqlDialect;

import java.util.Map;
import java.util.Optional;

public class DatabaseConfig {
    private MongoDBConfig mongodb;
    private DynamoDBConfig dynamodb;
    private CouchbaseConfig couchbase;
    private SqlConfig sql;

    public MongoDBConfig getMongodb() {
        return mongodb;
    }

    public void setMongodb(MongoDBConfig mongodb) {
        this.mongodb = mongodb;
    }

    public DynamoDBConfig getDynamodb() {
        return dynamodb;
    }

    public void setDynamodb(DynamoDBConfig dynamodb) {
        this.dynamodb = dynamodb;
    }

    public CouchbaseConfig getCouchbase() {
        return couchbase;
    }

    public void setCouchbase(CouchbaseConfig couchbase) {
        this.couchbase = couchbase;
    }

    public SqlConfig getSql() {
        return sql;
    }

    public void setSql(SqlConfig sql) {
        this.sql = sql;
    }

    public static class MongoDBConfig {
        private String connectionString;
        private String database;
        private String host;
        private Integer port;
        private String username;
        private String password;
        private String collection;

        public String getConnectionString() {
            return connectionString;
        }

        public void setConnectionString(String connectionString) {
            this.connectionString = connectionString;
        }

        public String getDatabase() {
            return database;
        }

        public void setDatabase(String database) {
            this.database = database;
        }

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public Integer getPort() {
            return port;
        }

        public void setPort(Integer port) {
            this.port = port;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getCollection() {
            return collection;
        }

        public void setCollection(String collection) {
            this.collection = collection;
        }
    }

    public static class DynamoDBConfig {
        private String region;
        private String endpoint;
        private String accessKey;
        private String secretKey;
        private String table;
        private Map<String, String> properties;

        public String getRegion() {
            return region;
        }

        public void setRegion(String region) {
            this.region = region;
        }

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getAccessKey() {
            return accessKey;
        }

        public void setAccessKey(String accessKey) {
            this.accessKey = accessKey;
        }

        public String getSecretKey() {
            return secretKey;
        }

        public void setSecretKey(String secretKey) {
            this.secretKey = secretKey;
        }

        public String getTable() {
            return table;
        }

        public void setTable(String table) {
            this.table = table;
        }

        public Map<String, String> getProperties() {
            return properties;
        }

        public void setProperties(Map<String, String> properties) {
            this.properties = properties;
        }
    }

    public static class CouchbaseConfig {
        private String bucketName;
        private String endpoint;
        private String username;
        private String password;
        private String table;
        private Map<String, String> properties;

        public String getBucketName() {
            return bucketName;
        }

        public void setBucketName(String bucketName) {
            this.bucketName = bucketName;
        }

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getTable() {
            return table;
        }

        public void setTable(String table) {
            this.table = table;
        }

        public Map<String, String> getProperties() {
            return properties;
        }

        public void setProperties(Map<String, String> properties) {
            this.properties = properties;
        }
    }

    public static class SqlConfig {
        private String endpoint;
        private String username;
        private String password;
        private SqlDialect sqlDialect;
        private String table;
        private Map<String, String> properties;

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public Optional<SqlDialect> getSqlDialect() {
            return sqlDialect == null ? Optional.empty() :  Optional.of(sqlDialect);
        }

        public void setSqlDialect(String sqlDialect) {
            this.sqlDialect = SqlDialect.valueOf(sqlDialect.toUpperCase());
        }

        public SqlDialect getEffectiveSqlDialect() {
            if (this.sqlDialect != null) {
                return this.sqlDialect;
            }
            String[] parts = this.endpoint.split(":", 3);
            if (parts.length < 2 || parts[1].isEmpty()) {
                throw new IllegalStateException("Cannot determine SQL dialect from endpoint: " + this.endpoint);
            }
            String dialect = parts[1].toLowerCase();
            if ("firebirdsql".equals(dialect)) dialect = "firebird";
            if ("informix-sqli".equals(dialect)) dialect = "informix";
            try {
                return SqlDialect.valueOf(dialect.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Unsupported SQL Dialect: " + dialect, e);
            }
        }

        public String getDriverClassName() {
            switch (this.getEffectiveSqlDialect()) {
                case MYSQL:
                    return "com.mysql.cj.jdbc.Driver";
                case MARIADB:
                    return "org.mariadb.jdbc.Driver";
                case POSTGRESQL:
                    return "org.postgresql.Driver";
                case SQLITE:
                    return "org.sqlite.JDBC";
                case H2:
                    return "org.h2.Driver";
                case SQLSERVER:
                    return "com.microsoft.sqlserver.jdbc.SQLServerDriver";
                case SYBASE:
                    return "com.sybase.jdbc4.jdbc.SybDriver";
                case FIREBIRD:
                    return "org.firebirdsql.jdbc.FBDriver";
                case INFORMIX:
                    return "com.informix.jdbc.IfxDriver";
                case ORACLE:
                    return "oracle.jdbc.OracleDriver";
                case DB2:
                    return "com.ibm.db2.jcc.DB2Driver";
                default:
                    throw new IllegalArgumentException("Unsupported SQL Dialect: " + this.getEffectiveSqlDialect());
            }
        }

        public String getTable() {
            return table;
        }

        public void setTable(String table) {
            this.table = table;
        }

        public Map<String, String> getProperties() {
            return properties;
        }

        public void setProperties(Map<String, String> properties) {
            this.properties = properties;
        }
    }
}
