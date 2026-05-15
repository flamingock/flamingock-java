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
package io.flamingock.cloud.api.request;

import io.flamingock.cloud.api.vo.CloudStageStatus;

import java.util.List;

public class StageRequest {
    private String name;

    private int order;

    /**
     * Per-stage status reported by the client. Nullable for back-compat with older clients;
     * servers must treat {@code null} as {@link CloudStageStatus#NOT_STARTED}.
     */
    private CloudStageStatus status;

    private List<ChangeRequest> changes;

    public StageRequest() {
    }

    public StageRequest(String name, int order, List<ChangeRequest> changes) {
        this(name, order, null, changes);
    }

    public StageRequest(String name, int order, CloudStageStatus status, List<ChangeRequest> changes) {
        this.name = name;
        this.order = order;
        this.status = status;
        this.changes = changes;
    }

    public String getName() {
        return name;
    }

    public int getOrder() {
        return order;
    }

    public CloudStageStatus getStatus() {
        return status;
    }

    public List<ChangeRequest> getChanges() {
        return changes;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setOrder(int order) {
        this.order = order;
    }

    public void setStatus(CloudStageStatus status) {
        this.status = status;
    }

    public void setChanges(List<ChangeRequest> changes) {
        this.changes = changes;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StageRequest that = (StageRequest) o;
        return order == that.order
                && java.util.Objects.equals(name, that.name)
                && status == that.status
                && java.util.Objects.equals(changes, that.changes);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(name, order, status, changes);
    }
}
