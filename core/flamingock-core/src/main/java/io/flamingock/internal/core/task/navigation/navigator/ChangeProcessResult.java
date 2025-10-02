package io.flamingock.internal.core.task.navigation.navigator;

import io.flamingock.internal.core.pipeline.execution.TaskSummary;

public class ChangeProcessResult {

    private final String changeId;
    private final TaskSummary summary;

    public ChangeProcessResult(String changeId, TaskSummary summary) {
        this.changeId = changeId;
        this.summary = summary;
    }

    public String getChangeId() {
        return changeId;
    }

    public TaskSummary getSummary() {
        return summary;
    }

    public boolean isFailed() {
        return false;
    }
}
