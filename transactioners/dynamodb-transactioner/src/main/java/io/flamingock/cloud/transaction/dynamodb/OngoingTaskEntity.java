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

import io.flamingock.internal.common.cloud.vo.OngoingStatus;
import io.flamingock.internal.core.cloud.transaction.TaskWithOngoingStatus;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;


@DynamoDbBean
public class OngoingTaskEntity {

    final static String tableName = "OngoingTasks";

    private String taskId;
    private String operation;

    public OngoingTaskEntity(String taskId, String operation) {
        this.taskId = taskId;
        this.operation = operation;
    }

    public OngoingTaskEntity() {
    }

    @DynamoDbPartitionKey
    @DynamoDbAttribute("taskId")
    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    @DynamoDbAttribute("operation")
    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public TaskWithOngoingStatus toOngoingStatus() {
        return new TaskWithOngoingStatus(this.taskId, OngoingStatus.valueOf(this.operation));
    }

}
