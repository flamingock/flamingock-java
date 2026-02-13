package io.flamingock.internal.common.core.error;

import java.time.Duration;

public class SteppableChangeExecutionException extends ChangeExecutionException {
    private final int step;

    public SteppableChangeExecutionException(String stageName, String changeId, int step, String message, String executionMode, Duration executionDuration, String targetSystemId, Throwable cause) {
        super(stageName, changeId, message, executionMode, executionDuration, targetSystemId, cause);
        this.step = step;
    }

    public SteppableChangeExecutionException(String changeId, int step, String message, Throwable cause) {
        super(changeId, message, cause);
        this.step = step;
    }

    public int getStep() {
        return step;
    }
}
