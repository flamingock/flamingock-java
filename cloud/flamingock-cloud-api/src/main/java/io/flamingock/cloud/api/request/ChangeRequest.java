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
package io.flamingock.cloud.api.request;


import io.flamingock.cloud.api.vo.CloudTargetSystemAuditMarkType;

//TODO add recoveryStrategy, so we can determin the acction in the server
public class ChangeRequest {

    private String id;

    private CloudTargetSystemAuditMarkType ongoingStatus;

    private boolean transactional;

    public ChangeRequest() {
    }

    public static ChangeRequest task(String id, boolean transactional) {
        return new ChangeRequest(id, CloudTargetSystemAuditMarkType.NONE, transactional);
    }

    public static ChangeRequest ongoingExecution(String id, boolean transactional) {
        return new ChangeRequest(id, CloudTargetSystemAuditMarkType.APPLIED, transactional);
    }

    public static ChangeRequest ongoingRollback(String id, boolean transactional) {
        return new ChangeRequest(id, CloudTargetSystemAuditMarkType.ROLLBACK, transactional);
    }

    public ChangeRequest(String id, CloudTargetSystemAuditMarkType ongoingStatus, boolean transactional) {
        this.id = id;
        this.ongoingStatus = ongoingStatus;
        this.transactional = transactional;
    }

    public String getId() {
        return id;
    }

    public CloudTargetSystemAuditMarkType getOngoingStatus() {
        return ongoingStatus;
    }

    public boolean isTransactional() {
        return transactional;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setOngoingStatus(CloudTargetSystemAuditMarkType ongoingStatus) {
        this.ongoingStatus = ongoingStatus;
    }

    public void setTransactional(boolean transactional) {
        this.transactional = transactional;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ChangeRequest that = (ChangeRequest) o;
        return transactional == that.transactional
                && java.util.Objects.equals(id, that.id)
                && java.util.Objects.equals(ongoingStatus, that.ongoingStatus);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(id, ongoingStatus, transactional);
    }
}