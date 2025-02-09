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

package io.flamingock.importer.cloud.dynamodb.legacy.mongock;

import io.flamingock.commons.utils.DynamoDBUtil;
import io.flamingock.importer.cloud.dynamodb.legacy.common.UserEntity;
import io.mongock.api.annotations.*;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactWriteItemsEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.DeleteTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest;

import static java.util.Collections.emptyList;


@ChangeUnit(id = "client-initializer", order = "1", author = "mongock")
public class ClientInitializerChangeUnit {

    public final static int INITIAL_CLIENTS = 10;
    public final static String CLIENTS_TABLE_NAME = "mongockClientTable";

    private static DynamoDBUtil dynamoDBUtil;

    private DynamoDbTable<UserEntity> table;

    @BeforeExecution
    public void beforeExecution(DynamoDbClient client) {
        dynamoDBUtil = new DynamoDBUtil(client);

        dynamoDBUtil.createTable(
                dynamoDBUtil.getAttributeDefinitions(UserEntity.pkName, UserEntity.skName),
                dynamoDBUtil.getKeySchemas(UserEntity.pkName, UserEntity.skName),
                dynamoDBUtil.getProvisionedThroughput(UserEntity.readCap, UserEntity.writeCap),
                CLIENTS_TABLE_NAME,
                emptyList(),
                emptyList()
        );
        client.describeTable(
                DescribeTableRequest.builder()
                        .tableName(CLIENTS_TABLE_NAME)
                        .build()
        );

        this.table = dynamoDBUtil.getEnhancedClient().table(CLIENTS_TABLE_NAME, TableSchema.fromBean(UserEntity.class));
    }

    @RollbackBeforeExecution
    public void rollbackBeforeExecution(DynamoDbClient client) {
        client.deleteTable(
                DeleteTableRequest.builder()
                        .tableName(CLIENTS_TABLE_NAME)
                        .build()
        );
    }

    @Execution
    public void execution(DynamoDbClient client) {
        final TransactWriteItemsEnhancedRequest.Builder writeRequestBuilder = TransactWriteItemsEnhancedRequest.builder();

        for (int i = 0; i < INITIAL_CLIENTS; i++)
            writeRequestBuilder.addPutItem(table, new UserEntity("nombre-" + i, "apellido-" + i));

        dynamoDBUtil.getEnhancedClient().transactWriteItems(writeRequestBuilder.build());
    }

    @RollbackExecution
    public void rollbackExecution(DynamoDbClient client) {
        final TransactWriteItemsEnhancedRequest.Builder writeRequestBuilder = TransactWriteItemsEnhancedRequest.builder();

        for (int i = 0; i < INITIAL_CLIENTS; i++)
            writeRequestBuilder.addDeleteItem(table, new UserEntity("nombre-" + i, "apellido-" + i));

        dynamoDBUtil.getEnhancedClient().transactWriteItems(writeRequestBuilder.build());
    }
}
