/*
 * Copyright 2023 Flamingock ("https://oss.flamingock.io")
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.flamingock.cloud.transaction.dynamodb.changes.unhappypath;

import io.flamingock.cloud.transaction.dynamodb.changes.common.UserEntity;
import io.flamingock.commons.utils.DynamoDBUtil;
import io.flamingock.core.api.annotations.NonLockGuarded;
import io.flamingock.core.api.annotations.Change;
import io.flamingock.core.api.annotations.Execution;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest;

import static java.util.Collections.emptyList;

@Change(id = "unhappy-create-table-clients", order = "001", transactional = false)
public class UnhappyCreateTableClientsChange {

    @Execution
    public void execution(@NonLockGuarded DynamoDbClient client) {

        DynamoDBUtil dynamoDBUtil = new DynamoDBUtil(client);

        dynamoDBUtil.createTable(
                dynamoDBUtil.getAttributeDefinitions(UserEntity.pkName, UserEntity.skName),
                dynamoDBUtil.getKeySchemas(UserEntity.pkName, UserEntity.skName),
                dynamoDBUtil.getProvisionedThroughput(UserEntity.readCap, UserEntity.writeCap),
                UserEntity.tableName,
                emptyList(),
                emptyList()
        );
        client.describeTable(
                DescribeTableRequest.builder()
                        .tableName(UserEntity.tableName)
                        .build()
        );
    }
}
