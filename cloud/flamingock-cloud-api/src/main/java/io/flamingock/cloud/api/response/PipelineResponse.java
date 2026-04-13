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
