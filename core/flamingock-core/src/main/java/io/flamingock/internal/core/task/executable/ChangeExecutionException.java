package io.flamingock.internal.core.task.executable;

import io.flamingock.internal.common.core.error.FlamingockException;

public class ChangeExecutionException extends FlamingockException {
    public ChangeExecutionException(String changeId,
                                    Throwable cause) {
        super(buildMessage(changeId, cause));
    }

    private static String buildMessage(String changeId, Throwable cause) {
        return String.format(
                "error trying to apply change with id[%s]: %s",
                changeId,
                cause.getMessage());
    }
}
