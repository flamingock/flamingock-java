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
package io.flamingock.cli.test;

import io.flamingock.cli.config.DatabaseConfig;
import io.flamingock.cli.config.FlamingockConfig;
import io.flamingock.internal.common.core.audit.AuditEntry;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

public class TestUtils {

    public static FlamingockConfig createMongoConfig() {
        FlamingockConfig config = new FlamingockConfig();
        config.setServiceIdentifier("test-service");

        DatabaseConfig databaseConfig = new DatabaseConfig();
        DatabaseConfig.MongoDBConfig mongoConfig = new DatabaseConfig.MongoDBConfig();
        mongoConfig.setConnectionString("mongodb://localhost:27017");
        mongoConfig.setDatabase("test-db");
        databaseConfig.setMongodb(mongoConfig);

        config.setAudit(databaseConfig);
        return config;
    }

    public static FlamingockConfig createDynamoConfig() {
        FlamingockConfig config = new FlamingockConfig();
        config.setServiceIdentifier("test-service");

        DatabaseConfig databaseConfig = new DatabaseConfig();
        DatabaseConfig.DynamoDBConfig dynamoConfig = new DatabaseConfig.DynamoDBConfig();
        dynamoConfig.setRegion("us-east-1");
        dynamoConfig.setEndpoint("http://localhost:8000");
        dynamoConfig.setAccessKey("testKey");
        dynamoConfig.setSecretKey("testSecret");
        databaseConfig.setDynamodb(dynamoConfig);

        config.setAudit(databaseConfig);
        return config;
    }

    public static FlamingockConfig createCouchbaseConfig() {
        FlamingockConfig config = new FlamingockConfig();
        config.setServiceIdentifier("test-service");

        DatabaseConfig databaseConfig = new DatabaseConfig();
        DatabaseConfig.CouchbaseConfig couchbaseConfig = new DatabaseConfig.CouchbaseConfig();
        couchbaseConfig.setEndpoint("couchbase://localhost:12110");
        couchbaseConfig.setUsername("test-user");
        couchbaseConfig.setPassword("test-password");
        couchbaseConfig.setBucketName("test-bucket");
        databaseConfig.setCouchbase(couchbaseConfig);

        config.setAudit(databaseConfig);
        return config;
    }

    public static FlamingockConfig createSqlConfig() {
        FlamingockConfig config = new FlamingockConfig();
        config.setServiceIdentifier("test-service");

        DatabaseConfig databaseConfig = new DatabaseConfig();
        DatabaseConfig.SqlConfig sqlConfig = new DatabaseConfig.SqlConfig();
        sqlConfig.setEndpoint("jdbc:sqlserver://localhost:1433");
        sqlConfig.setUsername("test-user");
        sqlConfig.setPassword("test-password");
        databaseConfig.setSql(sqlConfig);

        config.setAudit(databaseConfig);
        return config;
    }

    public static List<AuditEntry> createSampleAuditEntries() {
        AuditEntry entry1 = new AuditEntry(
            "exec-001",
            "stage-001",
            "change-001",
            "developer1",
            LocalDateTime.now().minusHours(1),
            AuditEntry.Status.APPLIED,
            AuditEntry.ChangeType.STANDARD_CODE,
            "com.example.Change001",
            "migrate",
            "sourceFile",
            1500,
            "server1.example.com",
            null,
            false,
            null
        );

        AuditEntry entry2 = new AuditEntry(
            "exec-002",
            "stage-002",
            "change-002",
            "developer2",
            LocalDateTime.now().minusMinutes(30),
            AuditEntry.Status.FAILED,
            AuditEntry.ChangeType.STANDARD_CODE,
            "com.example.Change002",
            "rollback",
            "sourceFile",
            800,
            "server2.example.com",
            null,
            false,
            "Connection timeout"
        );

        return Arrays.asList(entry1, entry2);
    }

    public static String createTempConfigFile(String content) {
        try {
            java.nio.file.Path tempFile = java.nio.file.Files.createTempFile("test-config", ".yml");
            java.nio.file.Files.write(tempFile, content.getBytes());
            return tempFile.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create temp config file", e);
        }
    }

    public static String getValidMongoYaml() {
        return "flamingock:\n" +
               "  service-identifier: \"test-cli\"\n" +
               "  audit:\n" +
               "    mongodb:\n" +
               "      connection-string: \"mongodb://localhost:27017\"\n" +
               "      database: \"test\"\n";
    }

    public static String getValidDynamoYaml() {
        return "flamingock:\n" +
               "  service-identifier: \"test-cli\"\n" +
               "  audit:\n" +
               "    dynamodb:\n" +
               "      region: \"us-east-1\"\n" +
               "      endpoint: \"http://localhost:8000\"\n" +
               "      access-key: \"testKey\"\n" +
               "      secret-key: \"testSecret\"\n";
    }

    public static String getValidCouchbaseYaml() {
        return "flamingock:\n" +
            "  service-identifier: \"test-cli\"\n" +
            "  audit:\n" +
            "    couchbase:\n" +
            "      endpoint: \"couchbase://localhost:12110\"\n" +
            "      username: \"test-user\"\n" +
            "      password: \"test-password\"\n" +
            "      bucket-name: \"test-bucket\"\n";
    }

    public static String getValidSqlYaml() {
        return "flamingock:\n" +
            "  service-identifier: \"test-cli\"\n" +
            "  audit:\n" +
            "    sql:\n" +
            "      endpoint: \"jdbc:sqlserver://localhost:1433\"\n" +
            "      username: \"test-user\"\n" +
            "      password: \"test-password\"\n" +
            "      sql-dialect: \"SqlServer\"\n";
    }
}
