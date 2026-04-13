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

public class PipelineResponse {

    private List<StageResponse> stages;

    public PipelineResponse() {
    }

    public PipelineResponse(List<StageResponse> stages) {
        this.stages = stages;
    }

    public List<StageResponse> getStages() { return stages; }
    public void setStages(List<StageResponse> stages) { this.stages = stages; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PipelineResponse that = (PipelineResponse) o;
        return java.util.Objects.equals(stages, that.stages);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(stages);
    }
}
