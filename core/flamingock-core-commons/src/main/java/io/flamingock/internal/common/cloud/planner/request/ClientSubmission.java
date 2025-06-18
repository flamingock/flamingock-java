package io.flamingock.internal.common.cloud.planner.request;

import java.util.List;

public class ClientSubmission {
    private final List<StageRequest> stages;

    public ClientSubmission(List<StageRequest> stages) {
        this.stages = stages;
    }

    public List<StageRequest> getStages() {
        return stages;
    }
}