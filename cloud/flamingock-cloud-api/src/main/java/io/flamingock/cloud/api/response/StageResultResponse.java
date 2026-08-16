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

import io.flamingock.cloud.api.vo.CloudPlannerVerdict;

import java.util.List;
import java.util.Objects;

/**
 * Result-side per-stage payload. Sibling of {@link StageResponse} on the operation side;
 * carries the server's per-stage {@code verdict} and per-change result records for the
 * client to write into {@code PipelineRun} via {@code markStageVerdict} +
 * {@code markStageAlreadyAppliedFromAudit}.
 *
 * <p>The server returns one of these for <strong>every stage in the submitted pipeline</strong>
 * regardless of whether the stage carries actionable work, so the client can iterate
 * uniformly without special-cases for "stage absent from response".
 */
public class StageResultResponse {

    private String name;

    private CloudPlannerVerdict verdict;

    private List<ChangeResultResponse> changes;

    public StageResultResponse() {
    }

    public StageResultResponse(String name,
                               CloudPlannerVerdict verdict,
                               List<ChangeResultResponse> changes) {
        this.name = name;
        this.verdict = verdict;
        this.changes = changes;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public CloudPlannerVerdict getVerdict() {
        return verdict;
    }

    public void setVerdict(CloudPlannerVerdict verdict) {
        this.verdict = verdict;
    }

    public List<ChangeResultResponse> getChanges() {
        return changes;
    }

    public void setChanges(List<ChangeResultResponse> changes) {
        this.changes = changes;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StageResultResponse that = (StageResultResponse) o;
        return Objects.equals(name, that.name)
                && verdict == that.verdict
                && Objects.equals(changes, that.changes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, verdict, changes);
    }
}
