/*
 * Copyright 2023 Flamingock (https://oss.flamingock.io)
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

package io.flamingock.cloud.transaction.dynamodb;

import io.flamingock.commons.utils.DynamoDBUtil;
import io.flamingock.core.engine.audit.domain.AuditItem;
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

    public DynamoDBTestHelper(DynamoDbClient client) {
        this.dynamoDBUtil = new DynamoDBUtil(client);
    }

    public boolean tableExists(String tableName) {
        return dynamoDBUtil.getDynamoDbClient().listTables().tableNames().contains(tableName);
    }

    public DynamoDbClient getDynamoDbClient() {
        return dynamoDBUtil.getDynamoDbClient();
    }

    public void insertOngoingExecution(String taskId) {
        dynamoDBUtil.createTable(
                dynamoDBUtil.getAttributeDefinitions("taskId", null),
                dynamoDBUtil.getKeySchemas("taskId", null),
                dynamoDBUtil.getProvisionedThroughput(5L, 5L),
                OngoingTaskEntity.tableName,
                emptyList(),
                emptyList()
        );

        DynamoDbTable<OngoingTaskEntity> table = dynamoDBUtil.getEnhancedClient().table(OngoingTaskEntity.tableName, TableSchema.fromBean(OngoingTaskEntity.class));
        table.putItem(
                PutItemEnhancedRequest.builder(OngoingTaskEntity.class)
                        .item(new OngoingTaskEntity(taskId, AuditItem.Operation.EXECUTION.toString()))
                        .build()
        );
        checkAtLeastOneOngoingTask();
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

    public void checkAtLeastOneOngoingTask() {
        checkOngoingTask(result -> result >= 1);
    }

    public void checkOngoingTask(Predicate<Long> predicate) {
        DynamoDbTable<OngoingTaskEntity> table = dynamoDBUtil.getEnhancedClient().table(OngoingTaskEntity.tableName, TableSchema.fromBean(OngoingTaskEntity.class));
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
