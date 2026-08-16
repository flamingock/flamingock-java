/*
 * Copyright 2026 Flamingock (https://www.flamingock.io)
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
package io.flamingock.cloud.api.response;

import io.flamingock.cloud.api.vo.CloudChangeStatus;

import java.util.Objects;

/**
 * Result-side per-change payload. Sibling of the operation-side {@link ChangeResponse} (which
 * carries {@code action}); this class carries the server's synthesised {@code status} so the
 * client can write rich per-change records into {@code PipelineRun} via
 * {@code markStageAlreadyAppliedFromAudit} (and downstream renderers).
 */
public class ChangeResultResponse {

    private String id;

    private CloudChangeStatus status;

    public ChangeResultResponse() {
    }

    public ChangeResultResponse(String id, CloudChangeStatus status) {
        this.id = id;
        this.status = status;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public CloudChangeStatus getStatus() {
        return status;
    }

    public void setStatus(CloudChangeStatus status) {
        this.status = status;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ChangeResultResponse that = (ChangeResultResponse) o;
        return Objects.equals(id, that.id) && status == that.status;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, status);
    }
}
