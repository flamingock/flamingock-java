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
package io.flamingock.dynamodb.kit;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.ResourceInUseException;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;

/**
 * Factory class for creating DynamoDB tables in test environments.
 * <p>
 * This utility provides methods to create common Flamingock-related tables
 * with appropriate schemas for testing purposes. All tables are created with
 * PAY_PER_REQUEST billing mode to simplify test setup.
 * <p>
 * All methods are idempotent - they safely handle cases where tables already exist.
 */
public class DynamoDBTableFactory {

    /**
     * Creates a Mongock origin table for migration testing.
     * <p>
     * This table uses a composite key structure matching Mongock's audit format:
     * <ul>
     *     <li>Partition key: executionId (String)</li>
     *     <li>Sort key: changeId (String)</li>
     * </ul>
     *
     * @param client    the DynamoDB client
     * @param tableName the name of the Mongock table to create
     */
    public static void createMongockTable(DynamoDbClient client, String tableName) {
        createTableSafe(client, CreateTableRequest.builder()
                .tableName(tableName)
                .keySchema(
                        KeySchemaElement.builder()
                                .attributeName("executionId")
                                .keyType(KeyType.HASH)
                                .build(),
                        KeySchemaElement.builder()
                                .attributeName("changeId")
                                .keyType(KeyType.RANGE)
                                .build()
                )
                .attributeDefinitions(
                        AttributeDefinition.builder()
                                .attributeName("executionId")
                                .attributeType(ScalarAttributeType.S)
                                .build(),
                        AttributeDefinition.builder()
                                .attributeName("changeId")
                                .attributeType(ScalarAttributeType.S)
                                .build()
                )
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .build());
    }

    /**
     * Creates a Flamingock audit table for storing change execution history.
     * <p>
     * This table uses a simple key structure:
     * <ul>
     *     <li>Partition key: partitionKey (String)</li>
     * </ul>
     *
     * @param client    the DynamoDB client
     * @param tableName the name of the audit table to create
     */
    public static void createAuditTable(DynamoDbClient client, String tableName) {
        createTableSafe(client, CreateTableRequest.builder()
                .tableName(tableName)
                .keySchema(
                        KeySchemaElement.builder()
                                .attributeName("partitionKey")
                                .keyType(KeyType.HASH)
                                .build()
                )
                .attributeDefinitions(
                        AttributeDefinition.builder()
                                .attributeName("partitionKey")
                                .attributeType(ScalarAttributeType.S)
                                .build()
                )
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .build());
    }

    /**
     * Creates a Flamingock lock table for distributed locking.
     * <p>
     * This table uses a simple key structure:
     * <ul>
     *     <li>Partition key: partitionKey (String)</li>
     * </ul>
     *
     * @param client    the DynamoDB client
     * @param tableName the name of the lock table to create
     */
    public static void createLockTable(DynamoDbClient client, String tableName) {
        createTableSafe(client, CreateTableRequest.builder()
                .tableName(tableName)
                .keySchema(
                        KeySchemaElement.builder()
                                .attributeName("partitionKey")
                                .keyType(KeyType.HASH)
                                .build()
                )
                .attributeDefinitions(
                        AttributeDefinition.builder()
                                .attributeName("partitionKey")
                                .attributeType(ScalarAttributeType.S)
                                .build()
                )
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .build());
    }

    /**
     * Creates a table safely, ignoring ResourceInUseException if the table already exists.
     *
     * @param client  the DynamoDB client
     * @param request the table creation request
     */
    private static void createTableSafe(DynamoDbClient client, CreateTableRequest request) {
        try {
            client.createTable(request);
        } catch (ResourceInUseException ignored) {
            // Table already exists, which is fine for idempotent test setup
        }
    }
}
