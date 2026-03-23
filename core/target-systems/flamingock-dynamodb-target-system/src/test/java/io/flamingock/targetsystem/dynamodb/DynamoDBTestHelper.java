/*
 * Copyright 2023 Flamingock (https://www.flamingock.io)
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
package io.flamingock.targetsystem.dynamodb;

import io.flamingock.internal.util.constants.CommunityPersistenceConstants;
import io.flamingock.internal.util.dynamodb.DynamoDBUtil;
import io.flamingock.internal.core.external.store.audit.domain.AuditContextBundle;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.PutItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.ScanEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.util.function.Predicate;

import static java.util.Collections.emptyList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DynamoDBTestHelper {
    public final DynamoDBUtil dynamoDBUtil;
    public final String tableName = CommunityPersistenceConstants.DEFAULT_MARKER_STORE_NAME;

    public DynamoDBTestHelper(DynamoDbClient client) {
        this.dynamoDBUtil = new DynamoDBUtil(client);
    }

    public boolean tableExists(String tableName) {
        return dynamoDBUtil.getDynamoDBClient().listTables().tableNames().contains(tableName);
    }

    public DynamoDbClient getDynamoDBClient() {
        return dynamoDBUtil.getDynamoDBClient();
    }

    public void insertOngoingExecution(String taskId) {
        dynamoDBUtil.createTable(
                dynamoDBUtil.getAttributeDefinitions("taskId", null),
                dynamoDBUtil.getKeySchemas("taskId", null),
                dynamoDBUtil.getProvisionedThroughput(5L, 5L),
                tableName,
                emptyList(),
                emptyList()
        );

        DynamoDbTable<OngoingTaskEntity> table = dynamoDBUtil.getEnhancedClient().table(tableName, TableSchema.fromBean(OngoingTaskEntity.class));
        table.putItem(
                PutItemEnhancedRequest.builder(OngoingTaskEntity.class)
                        .item(new OngoingTaskEntity(taskId, AuditContextBundle.Operation.EXECUTION.toString()))
                        .build()
        );
        checkEmptyTargetSystemAudiMarker();
    }

    public <T> void checkCount(DynamoDbTable<T> table, int count) {
        long result = table
                .scan(ScanEnhancedRequest.builder()
                        .consistentRead(true)
                        .build()
                )
                .items()
                .stream()
                .count();
        assertEquals(count, (int) result);
    }

    public void checkEmptyTargetSystemAudiMarker() {
        checkOngoingTask(result -> result == 0);
    }

    public void checkOngoingTask(Predicate<Long> predicate) {
        DynamoDbTable<OngoingTaskEntity> table = dynamoDBUtil.getEnhancedClient().table(tableName, TableSchema.fromBean(OngoingTaskEntity.class));
        long result = table
                .scan(ScanEnhancedRequest.builder()
                        .consistentRead(true)
                        .build()
                )
                .items()
                .stream()
                .count();
        assertTrue(predicate.test(result));
    }
}
