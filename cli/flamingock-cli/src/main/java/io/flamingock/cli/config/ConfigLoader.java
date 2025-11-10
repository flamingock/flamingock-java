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

import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

public class ConfigLoader {

    public static FlamingockConfig loadConfig(String configFilePath) throws IOException {
        File configFile = new File(configFilePath);
        if (!configFile.exists()) {
            throw new IOException("Configuration file not found: " + configFilePath);
        }

        Yaml yaml = new Yaml();
        try (InputStream input = new FileInputStream(configFile)) {
            Map<String, Object> yamlData = yaml.load(input);
            return parseConfig(yamlData);
        }
    }

    @SuppressWarnings("unchecked")
    private static FlamingockConfig parseConfig(Map<String, Object> yamlData) {
        FlamingockConfig config = new FlamingockConfig();

        Map<String, Object> flamingockData = (Map<String, Object>) yamlData.get("flamingock");
        if (flamingockData == null) {
            throw new IllegalArgumentException("Missing 'flamingock' section in configuration file");
        }

        // Parse service identifier
        if (flamingockData.containsKey("service-identifier")) {
            config.setServiceIdentifier((String) flamingockData.get("service-identifier"));
        }

        // Parse audit configuration
        Map<String, Object> auditData = (Map<String, Object>) flamingockData.get("audit");
        if (auditData != null) {
            DatabaseConfig databaseConfig = new DatabaseConfig();

            // Parse MongoDB config
            Map<String, Object> mongoData = (Map<String, Object>) auditData.get("mongodb");
            if (mongoData != null) {
                DatabaseConfig.MongoDBConfig mongoConfig = new DatabaseConfig.MongoDBConfig();
                mongoConfig.setConnectionString((String) mongoData.get("connection-string"));
                mongoConfig.setDatabase((String) mongoData.get("database"));
                mongoConfig.setHost((String) mongoData.get("host"));
                mongoConfig.setUsername((String) mongoData.get("username"));
                mongoConfig.setPassword((String) mongoData.get("password"));
                if (mongoData.get("port") != null) {
                    mongoConfig.setPort((Integer) mongoData.get("port"));
                }
                databaseConfig.setMongodb(mongoConfig);
            }

            // Parse DynamoDB config
            Map<String, Object> dynamoData = (Map<String, Object>) auditData.get("dynamodb");
            if (dynamoData != null) {
                DatabaseConfig.DynamoDBConfig dynamoConfig = new DatabaseConfig.DynamoDBConfig();
                dynamoConfig.setRegion((String) dynamoData.get("region"));
                dynamoConfig.setEndpoint((String) dynamoData.get("endpoint"));
                dynamoConfig.setAccessKey((String) dynamoData.get("access-key"));
                dynamoConfig.setSecretKey((String) dynamoData.get("secret-key"));
                if (dynamoData.get("properties") != null) {
                    dynamoConfig.setProperties((Map<String, String>) dynamoData.get("properties"));
                }
                databaseConfig.setDynamodb(dynamoConfig);
            }

            // Parse Couchbase config
            Map<String, Object> couchbaseData = (Map<String, Object>) auditData.get("couchbase");
            if (couchbaseData != null) {
                DatabaseConfig.CouchbaseConfig couchbaseConfig = new DatabaseConfig.CouchbaseConfig();
                couchbaseConfig.setBucketName((String) couchbaseData.get("bucket-name"));
                couchbaseConfig.setEndpoint((String) couchbaseData.get("endpoint"));
                couchbaseConfig.setUsername((String) couchbaseData.get("username"));
                couchbaseConfig.setPassword((String) couchbaseData.get("password"));
                if (couchbaseData.get("properties") != null) {
                    couchbaseConfig.setProperties((Map<String, String>) couchbaseData.get("properties"));
                }
                databaseConfig.setCouchbase(couchbaseConfig);
            }

            config.setAudit(databaseConfig);
        }

        return config;
    }

    public static DatabaseType detectDatabaseType(FlamingockConfig config) {
        if (config.getAudit() == null) {
            throw new IllegalArgumentException("No audit configuration found");
        }

        boolean hasMongoDB = config.getAudit().getMongodb() != null;
        boolean hasDynamoDB = config.getAudit().getDynamodb() != null;
        boolean hasCouchbase = config.getAudit().getCouchbase() != null;

        if (hasMongoDB && hasDynamoDB) { // TODO: Check if more than one DB is configured
            throw new IllegalArgumentException("Multiple database configurations found. Please configure only one database type.");
        }

        if (hasMongoDB) {
            return DatabaseType.MONGODB;
        } else if (hasDynamoDB) {
            return DatabaseType.DYNAMODB;
        } else if (hasCouchbase) {
            return DatabaseType.COUCHBASE;
        } else {
            throw new IllegalArgumentException("No supported database configuration found. Please configure MongoDB, DynamoDB or Couchbase.");
        }
    }

    public enum DatabaseType {
        MONGODB, DYNAMODB, COUCHBASE
    }
}
