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

import io.flamingock.internal.common.cloud.vo.TargetSystemAuditMarkType;
import io.flamingock.internal.core.external.targets.mark.TargetSystemAuditMark;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;


@DynamoDbBean
public class OngoingTaskEntity {

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

    public TargetSystemAuditMark toOngoingStatus() {
        return new TargetSystemAuditMark(this.taskId, TargetSystemAuditMarkType.valueOf(this.operation));
    }

}
