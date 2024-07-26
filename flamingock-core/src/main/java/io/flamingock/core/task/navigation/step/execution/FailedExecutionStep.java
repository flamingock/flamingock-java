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

package io.flamingock.core.task.navigation.step.execution;

import io.flamingock.core.task.navigation.step.ExecutableStep;
import io.flamingock.core.task.navigation.step.FailedWithErrorStep;
import io.flamingock.core.task.navigation.step.afteraudit.AfterExecutionAuditStep;
import io.flamingock.core.task.navigation.step.afteraudit.FailedExecutionOrAuditStep;
import io.flamingock.core.task.executable.ExecutableTask;
import io.flamingock.commons.utils.Result;

public final class FailedExecutionStep extends ExecutionStep implements FailedWithErrorStep {
    private final Throwable throwable;

    public static FailedExecutionStep instance(ExecutableStep initialStep, long executionTimeMillis, Throwable throwable) {
        return new FailedExecutionStep(initialStep.getTask(), executionTimeMillis, throwable);
    }

    private FailedExecutionStep(ExecutableTask task, long executionTimeMillis, Throwable throwable) {
        super(task, false, executionTimeMillis);
        this.throwable = throwable;
    }

    @Override
    public Throwable getError() {
        return throwable;
    }

    @Override
    public AfterExecutionAuditStep applyAuditResult(Result auditResult) {
        return FailedExecutionOrAuditStep.instance(task, auditResult);
    }

}
