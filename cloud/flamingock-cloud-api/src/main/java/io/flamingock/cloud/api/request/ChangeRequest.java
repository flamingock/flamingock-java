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


import com.fasterxml.jackson.annotation.JsonInclude;
import io.flamingock.cloud.api.vo.CloudChangeStatus;
import io.flamingock.cloud.api.vo.CloudTargetSystemAuditMarkType;

//TODO add recoveryStrategy, so we can determin the acction in the server
public class ChangeRequest {

    private String id;

    private CloudTargetSystemAuditMarkType ongoingStatus;

    /**
     * Per-change status reported by the client — mirrors the operation-side
     * {@code ChangeResult.status} currently held on the client's {@code PipelineRun}. The
     * server uses this as informational input when synthesising the response: it never
     * contradicts the client's positive report (e.g. {@code APPLIED} stays {@code APPLIED},
     * not downgraded to {@code ALREADY_APPLIED}), and it respects {@code FAILED} /
     * {@code ROLLED_BACK} reports so it doesn't ask the client to retry indefinitely.
     *
     * <p>{@code null} on the wire means the operation has nothing to report yet
     * (equivalent to {@code NOT_REACHED}). Serialised as field-absence so the wire shape is
     * forward-compatible with older mocks/expectations that don't set this field.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private CloudChangeStatus currentStatus;

    private boolean transactional;

    public ChangeRequest() {
    }

    public static ChangeRequest change(String id, boolean transactional) {
        return new ChangeRequest(id, CloudTargetSystemAuditMarkType.NONE, null, transactional);
    }

    public static ChangeRequest ongoingExecution(String id, boolean transactional) {
        return new ChangeRequest(id, CloudTargetSystemAuditMarkType.APPLIED, null, transactional);
    }

    public static ChangeRequest ongoingRollback(String id, boolean transactional) {
        return new ChangeRequest(id, CloudTargetSystemAuditMarkType.ROLLED_BACK, null, transactional);
    }

    public ChangeRequest(String id,
                         CloudTargetSystemAuditMarkType ongoingStatus,
                         boolean transactional) {
        this(id, ongoingStatus, null, transactional);
    }

    public ChangeRequest(String id,
                         CloudTargetSystemAuditMarkType ongoingStatus,
                         CloudChangeStatus currentStatus,
                         boolean transactional) {
        this.id = id;
        this.ongoingStatus = ongoingStatus;
        this.currentStatus = currentStatus;
        this.transactional = transactional;
    }

    public String getId() {
        return id;
    }

    public CloudTargetSystemAuditMarkType getOngoingStatus() {
        return ongoingStatus;
    }

    public CloudChangeStatus getCurrentStatus() {
        return currentStatus;
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

    public void setCurrentStatus(CloudChangeStatus currentStatus) {
        this.currentStatus = currentStatus;
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
                && java.util.Objects.equals(ongoingStatus, that.ongoingStatus)
                && java.util.Objects.equals(currentStatus, that.currentStatus);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(id, ongoingStatus, currentStatus, transactional);
    }
}