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

import java.util.List;
import java.util.Objects;

/**
 * Result-side sibling of {@code ExecutionPlanResponse.stages}. Carries the server's
 * synthesised per-stage {@code verdict} and per-change {@code status} for the entire
 * submitted pipeline, in a shape the client iterates uniformly to feed
 * {@code PipelineRun.markStageVerdict} and
 * {@code PipelineRun.markStageAlreadyAppliedFromAudit}.
 *
 * <p>The two halves of the response mirror the two halves of the core-side
 * {@code PipelineRun} two-writer model: the operation side ({@code stages[]}) tells the
 * executor what to do; the result side (this class) tells the client's planner what facts
 * to record.
 */
public class PipelineResultResponse {

    private List<StageResultResponse> stages;

    public PipelineResultResponse() {
    }

    public PipelineResultResponse(List<StageResultResponse> stages) {
        this.stages = stages;
    }

    public List<StageResultResponse> getStages() {
        return stages;
    }

    public void setStages(List<StageResultResponse> stages) {
        this.stages = stages;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PipelineResultResponse that = (PipelineResultResponse) o;
        return Objects.equals(stages, that.stages);
    }

    @Override
    public int hashCode() {
        return Objects.hash(stages);
    }
}
