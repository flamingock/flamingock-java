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
package io.flamingock.internal.common.cloud.planner.request;


import io.flamingock.internal.common.cloud.vo.TargetSystemAuditMarkType;

//TODO add recoveryStrategy, so we can determin the acction in the server
public class TaskRequest {

    private final String id;

    private final TargetSystemAuditMarkType ongoingStatus;

    private final boolean transactional;

    public static TaskRequest task(String id, boolean transactional) {
        return new TaskRequest(id, TargetSystemAuditMarkType.NONE, transactional);
    }

    public static TaskRequest ongoingExecution(String id, boolean transactional) {
        return new TaskRequest(id, TargetSystemAuditMarkType.APPLIED, transactional);
    }

    public static TaskRequest ongoingRollback(String id, boolean transactional) {
        return new TaskRequest(id, TargetSystemAuditMarkType.ROLLBACK, transactional);
    }

    public TaskRequest(String id, TargetSystemAuditMarkType ongoingStatus, boolean transactional) {
        this.id = id;
        this.ongoingStatus = ongoingStatus;
        this.transactional = transactional;
    }

    public String getId() {
        return id;
    }

    public TargetSystemAuditMarkType getOngoingStatus() {
        return ongoingStatus;
    }

    public boolean isTransactional() {
        return transactional;
    }
}