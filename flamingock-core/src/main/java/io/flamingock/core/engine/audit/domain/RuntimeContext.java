/*
 * Copyright 2023 Flamingock (https://oss.flamingock.io)
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

package io.flamingock.core.engine.audit.domain;

import io.flamingock.core.task.navigation.step.FailedWithErrorStep;
import io.flamingock.core.task.navigation.step.StartStep;
import io.flamingock.core.task.navigation.step.TaskStep;
import io.flamingock.core.task.navigation.step.complete.failed.CompleteAutoRolledBackStep;
import io.flamingock.core.task.navigation.step.execution.ExecutionStep;
import io.flamingock.core.task.navigation.step.rolledback.ManualRolledBackStep;

import java.time.LocalDateTime;
import java.util.Optional;

public final class RuntimeContext {


    public static Builder builder() {
        return new Builder();
    }

    private enum ExecutionResult {SUCCESS, FAILED}

    private final String stageName;

    private final ExecutionResult executionResult;
    private final long duration;

    private final LocalDateTime executedAt;

    private final String methodExecutor;

    private final Throwable error;

    private RuntimeContext(String stageName,
                           ExecutionResult executionResult,
                           long duration,
                           LocalDateTime executedAt,
                           String methodExecutor,
                           Throwable error) {
        this.stageName = stageName;
        this.executionResult = executionResult;
        this.duration = duration;
        this.executedAt = executedAt;
        this.methodExecutor = methodExecutor;
        this.error = error;
    }

    public String getStageName() {
        return stageName;
    }

    public long getDuration() {
        return duration;
    }

    public LocalDateTime getExecutedAt() {
        return executedAt;
    }

    public String getMethodExecutor() {
        return methodExecutor;
    }

    public Optional<Throwable> getError() {
        return Optional.ofNullable(error);
    }

    public boolean isSuccess() {
        return executionResult == ExecutionResult.SUCCESS;
    }

    public boolean isFailed() {
        return executionResult == ExecutionResult.FAILED;
    }


    public static class Builder {

        private ExecutionResult executionResult = ExecutionResult.SUCCESS;
        private String stageName;

        private long duration = -1L;

        private LocalDateTime executedAt;

        private String methodExecutor;

        private Throwable error;

        private Builder() {
        }

        public Builder setStartStep(StartStep taskStep) {
            duration = 0L;
            methodExecutor = taskStep.getTask().getExecutionMethodName();
            stageName = taskStep.getTask().getStageName();
            setFailure(taskStep);
            return this;
        }

        public Builder setExecutionStep(ExecutionStep taskStep) {
            duration = taskStep.getDuration();
            methodExecutor = taskStep.getTask().getExecutionMethodName();
            stageName = taskStep.getTask().getStageName();
            setFailure(taskStep);
            return this;
        }

        public Builder setManualRollbackStep(ManualRolledBackStep rolledBackStep) {
            duration = rolledBackStep.getDuration();
            methodExecutor = rolledBackStep.getRollback().getRollbackMethodName();
            stageName = rolledBackStep.getTask().getStageName();
            setFailure(rolledBackStep);
            return this;
        }

        public Builder setAutoRollbackStep(CompleteAutoRolledBackStep rolledBackStep) {
            duration = 0L;
            methodExecutor = "native_db_engine";
            stageName = rolledBackStep.getTask().getStageName();
            setFailure(rolledBackStep);
            return this;
        }

        private void setFailure(TaskStep taskStep) {
            if (taskStep instanceof FailedWithErrorStep) {
                executionResult = ExecutionResult.FAILED;
                error = ((FailedWithErrorStep) taskStep).getError();
            } else {
                executionResult = ExecutionResult.SUCCESS;
                error = null;
            }
        }


        public Builder setExecutedAt(LocalDateTime executedAt) {
            this.executedAt = executedAt;
            return this;
        }

        public RuntimeContext build() {
            if (methodExecutor == null) {
                throw new IllegalArgumentException("[methodExecutor] cannot be null when building RuntimeContext");
            }
            if (executedAt == null) {
                throw new IllegalArgumentException("[executedAt] cannot be null when building RuntimeContext");
            }
            return new RuntimeContext(stageName, executionResult, duration, executedAt, methodExecutor, error);

        }
    }
}
