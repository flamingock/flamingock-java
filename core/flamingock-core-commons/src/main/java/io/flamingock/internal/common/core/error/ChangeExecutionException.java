/*
 * Copyright 2023 Flamingock (https://www.flamingock.io)
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
package io.flamingock.internal.common.core.error;

import java.time.Duration;

/**
 * Exception thrown when a Change execution fails.
 *
 * <p>This exception provides rich context about the failed change execution including:
 * <ul>
 *   <li>Change ID and stage information</li>
 *   <li>Execution mode (transactional, non-transactional, shared-transactional)</li>
 *   <li>Execution duration for performance analysis</li>
 *   <li>Target system information</li>
 *   <li>Original cause with preserved stack trace</li>
 * </ul>
 *
 * <p>This exception should be used instead of generic {@link FlamingockException}
 * when change execution fails, as it provides much better debugging context.
 *
 * @since 6.0.0
 */
public class ChangeExecutionException extends FlamingockException {

    private final String changeId;
    private final String stageName;
    private final String executionMode;
    private final Duration executionDuration;
    private final String targetSystemId;

    /**
     * Creates a new ChangeExecutionException with full execution context.
     *
     * @param message           descriptive error message
     * @param changeId          the ID of the failed change
     * @param stageName         the name of the stage containing the change
     * @param executionMode     the execution mode (e.g., "transactional", "non-transactional")
     * @param executionDuration how long the change took before failing
     * @param targetSystemId    the target system where the change was being applied
     * @param cause             the underlying exception that caused the failure
     */
    public ChangeExecutionException(String stageName,
                                    String changeId,
                                    String message,
                                    String executionMode,
                                    Duration executionDuration,
                                    String targetSystemId,
                                    Throwable cause) {
        super(buildContextualMessage(stageName, changeId, message, executionMode, executionDuration, targetSystemId), cause);
        this.changeId = changeId;
        this.stageName = stageName;
        this.executionMode = executionMode;
        this.executionDuration = executionDuration;
        this.targetSystemId = targetSystemId;
    }

    /**
     * Creates a new ChangeExecutionException with minimal context (for backward compatibility).
     *
     * @param changeId the ID of the failed change
     * @param message  descriptive error message
     * @param cause    the underlying exception that caused the failure
     */
    public ChangeExecutionException(String changeId, String message, Throwable cause) {
        this(null, changeId, message, null, null, null, cause);
    }

    private static String buildContextualMessage(String stageName,
                                                 String changeId,
                                                 String message,
                                                 String executionMode,
                                                 Duration executionDuration,
                                                 String targetSystemId) {
        StringBuilder contextMessage = new StringBuilder(message);

        if (changeId != null) {
            contextMessage.append("\n  Change ID: ").append(changeId);
        }
        if (stageName != null) {
            contextMessage.append("\n  Stage: ").append(stageName);
        }
        if (executionMode != null) {
            contextMessage.append("\n  Execution Mode: ").append(executionMode);
        }
        if (executionDuration != null) {
            contextMessage.append("\n  Execution Duration: ").append(formatDuration(executionDuration));
        }
        if (targetSystemId != null) {
            contextMessage.append("\n  Target System: ").append(targetSystemId);
        }

        return contextMessage.toString();
    }

    private static String formatDuration(Duration duration) {
        long millis = duration.toMillis();
        if (millis < 1000) {
            return millis + "ms";
        } else if (millis < 60000) {
            return String.format("%.1fs", millis / 1000.0);
        } else {
            return String.format("%.1fm", millis / 60000.0);
        }
    }

    /**
     * @return the ID of the failed change
     */
    public String getChangeId() {
        return changeId;
    }

    /**
     * @return the name of the stage containing the failed change
     */
    public String getStageName() {
        return stageName;
    }

    /**
     * @return the execution mode when the change failed
     */
    public String getExecutionMode() {
        return executionMode;
    }

    /**
     * @return how long the change took before failing
     */
    public Duration getExecutionDuration() {
        return executionDuration;
    }

    /**
     * @return the target system ID where the change was being applied
     */
    public String getTargetSystemId() {
        return targetSystemId;
    }
}