package io.flamingock.common.test.cloud.prototype;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PrototypeClientSubmission {
    private final List<PrototypeStage> stages = new ArrayList<>();

    public PrototypeClientSubmission(PrototypeStage first, PrototypeStage ...others) {
        stages.add(first);
        stages.addAll(Arrays.asList(others));
    }

    public List<PrototypeStage> getStages() {
        return stages;
    }
}
