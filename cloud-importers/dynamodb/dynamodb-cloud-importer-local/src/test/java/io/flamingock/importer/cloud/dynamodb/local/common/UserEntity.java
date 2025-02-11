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

package io.flamingock.importer.cloud.dynamodb.local.common;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;


@DynamoDbBean
public class UserEntity {

    public static final String tableName = "test_table";
    public static final String pkName = "PartitionKey";
    public static final String skName = "SortKey";
    public static final Long readCap = 5L;
    public static final Long writeCap = 5L;

    private String partitionKey;
    private String sortKey;
    private String firstName;
    private String lastName;

    public UserEntity(String firstName, String lastName) {
        this.partitionKey = partitionKey(firstName, lastName);
        this.sortKey = "Usuario";
        this.firstName = firstName;
        this.lastName = lastName;
    }

    public UserEntity() {
    }

    public String partitionKey(
            String firstName,
            String lastName
    ) {
        return firstName + ' ' + lastName;
    }

    @DynamoDbPartitionKey
    @DynamoDbAttribute(pkName)
    public String getPartitionKey() {
        return partitionKey;
    }

    public void setPartitionKey(String partitionKey) {
        this.partitionKey = partitionKey;
    }

    @DynamoDbSortKey
    @DynamoDbAttribute(skName)
    public String getSortKey() {
        return sortKey;
    }

    public void setSortKey(String sortKey) {
        this.sortKey = sortKey;
    }

    @DynamoDbAttribute("FirstName")
    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    @DynamoDbAttribute("LastName")
    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }
}
