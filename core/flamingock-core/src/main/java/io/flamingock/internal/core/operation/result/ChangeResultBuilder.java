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
package io.flamingock.internal.core.operation.result;

import io.flamingock.internal.common.core.response.data.ChangeResult;
import io.flamingock.internal.common.core.response.data.ChangeStatus;
import io.flamingock.internal.common.core.task.TaskDescriptor;
import io.flamingock.internal.core.task.executable.ExecutableTask;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Builder for creating ChangeResult instances from task execution data.
 */
public class ChangeResultBuilder {

    private String changeId;
    private String author;
    private ChangeStatus status;
    private long durationMs;
    private String targetSystemId;
    private Throwable error;
    private LocalDateTime startTime;

    public ChangeResultBuilder() {
    }

    public ChangeResultBuilder fromTask(ExecutableTask task) {
        TaskDescriptor descriptor = task.getDescriptor();
        this.changeId = descriptor.getId();
        this.author = descriptor.getAuthor();
        if (descriptor.getTargetSystem() != null) {
            this.targetSystemId = descriptor.getTargetSystem().getId();
        }
        return this;
    }

    public ChangeResultBuilder startTimer() {
        this.startTime = LocalDateTime.now();
        return this;
    }

    public ChangeResultBuilder stopTimer() {
        if (startTime != null) {
            this.durationMs = Duration.between(startTime, LocalDateTime.now()).toMillis();
        }
        return this;
    }

    public ChangeResultBuilder status(ChangeStatus status) {
        this.status = status;
        return this;
    }

    public ChangeResultBuilder applied() {
        this.status = ChangeStatus.APPLIED;
        return this;
    }

    public ChangeResultBuilder alreadyApplied() {
        this.status = ChangeStatus.ALREADY_APPLIED;
        return this;
    }

    public ChangeResultBuilder failed(Throwable error) {
        this.status = ChangeStatus.FAILED;
        this.error = error;
        return this;
    }

    public ChangeResultBuilder rolledBack() {
        this.status = ChangeStatus.ROLLED_BACK;
        return this;
    }

    public ChangeResultBuilder error(Throwable error) {
        this.error = error;
        return this;
    }

    public ChangeResultBuilder notReached() {
        this.status = ChangeStatus.NOT_REACHED;
        return this;
    }

    public ChangeResultBuilder durationMs(long durationMs) {
        this.durationMs = durationMs;
        return this;
    }

    public ChangeResult build() {
        ChangeResult.Builder builder = ChangeResult.builder()
                .changeId(changeId)
                .author(author)
                .status(status)
                .durationMs(durationMs)
                .targetSystemId(targetSystemId);

        if (error != null) {
            builder.error(error);
        }

        return builder.build();
    }
}
