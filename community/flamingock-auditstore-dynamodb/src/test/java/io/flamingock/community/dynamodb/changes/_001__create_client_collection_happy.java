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
package io.flamingock.community.dynamodb.changes;

import io.flamingock.api.annotations.Change;
import io.flamingock.api.annotations.Apply;
import io.flamingock.api.annotations.TargetSystem;
import io.flamingock.api.annotations.NonLockGuarded;
import io.flamingock.community.dynamodb.changes.common.DynamoDBUtil;
import io.flamingock.community.dynamodb.changes.common.UserEntity;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest;

import static java.util.Collections.emptyList;

@TargetSystem(id = "dynamodb")
@Change(id = "table-create", transactional = false, author = "aperezdieppa")
public class _001__create_client_collection_happy {

    private final DynamoDBUtil dynamoDBUtil = new DynamoDBUtil();

    @Apply
    public void execution(@NonLockGuarded DynamoDbClient client) {

        dynamoDBUtil.createTable(
                client,
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
