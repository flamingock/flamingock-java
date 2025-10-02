package io.flamingock.internal.core.task.navigation;

import io.flamingock.internal.core.pipeline.execution.TaskSummary;
import io.flamingock.internal.core.task.navigation.navigator.ChangeProcessResult;

public class FailedChangeProcessResult extends ChangeProcessResult {
    private final Throwable exception;

    public FailedChangeProcessResult(String changeId, TaskSummary summary, Throwable exception) {
        super(changeId, summary);
        this.exception = exception;
    }

    public Throwable getException() {
        return exception;
    }

    @Override
    public boolean isFailed() {
        return true;
    }
}
