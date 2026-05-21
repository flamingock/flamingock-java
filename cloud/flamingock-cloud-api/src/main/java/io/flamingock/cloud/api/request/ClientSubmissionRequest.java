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

import java.util.List;

/**
 * Payload submitted by the client describing the pipeline state for an execution-plan request.
 * Stages are grouped into {@link StageBlockRequest}s where the block list order conveys the
 * dependency order — {@code blocks.get(0)} must complete before {@code blocks.get(1)} may run.
 *
 * <p>Block membership is owned by the client's {@code PipelineRun.getStageBlocks()}; the server
 * consumes the list as-is, with no {@code StageType}-based regrouping.
 */
public class ClientSubmissionRequest {

    private List<StageBlockRequest> blocks;

    public ClientSubmissionRequest() {
    }

    public ClientSubmissionRequest(List<StageBlockRequest> blocks) {
        this.blocks = blocks;
    }

    public List<StageBlockRequest> getBlocks() {
        return blocks;
    }

    public void setBlocks(List<StageBlockRequest> blocks) {
        this.blocks = blocks;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ClientSubmissionRequest that = (ClientSubmissionRequest) o;
        return java.util.Objects.equals(blocks, that.blocks);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(blocks);
    }
}
