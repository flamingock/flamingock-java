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
package io.flamingock.common.test.cloud;

import io.flamingock.internal.common.cloud.audit.AuditEntryRequest;

public class AuditRequestExpectation {
    private final String taskId;
    private final AuditEntryRequest.Status state;
    private final String executionId;

    public AuditRequestExpectation(String executionId, String taskId, AuditEntryRequest.Status state) {
        this.executionId = executionId;
        this.taskId = taskId;
        this.state = state;
    }

    public String getTaskId() {
        return taskId;
    }

    public AuditEntryRequest.Status getState() {
        return state;
    }

    public String getExecutionId() {
        return executionId;
    }
}
