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
package io.flamingock.cloud.api.request;

import io.flamingock.api.StageCategory;

import java.util.List;
import java.util.Objects;

/**
 * Structural unit of a cloud execution-plan submission: a block of stages that must complete
 * before the next block in the {@code ClientSubmissionRequest.blocks} list may run. Block
 * membership is owned by the client's {@code PipelineRun}; the server consumes the block list
 * as-is and does NOT regroup stages by {@link #category}.
 *
 * <p>The {@code category} field is metadata (diagnostics, future use). Two blocks may share the
 * same {@link StageCategory} — the server iterates the block list in order and never collapses by
 * category.
 */
public class StageBlockRequest {

    private StageCategory category;
    private List<StageRequest> stages;

    public StageBlockRequest() {
    }

    public StageBlockRequest(StageCategory category, List<StageRequest> stages) {
        this.category = category;
        this.stages = stages;
    }

    public StageCategory getCategory() {
        return category;
    }

    public void setCategory(StageCategory category) {
        this.category = category;
    }

    public List<StageRequest> getStages() {
        return stages;
    }

    public void setStages(List<StageRequest> stages) {
        this.stages = stages;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StageBlockRequest that = (StageBlockRequest) o;
        return category == that.category && Objects.equals(stages, that.stages);
    }

    @Override
    public int hashCode() {
        return Objects.hash(category, stages);
    }
}
