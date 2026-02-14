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
package io.flamingock.internal.core.task.navigation.step.afteraudit;

import io.flamingock.internal.core.task.navigation.step.FailedWithErrorStep;
import io.flamingock.internal.core.task.navigation.step.RollableFailedStep;
import io.flamingock.internal.core.task.navigation.step.SuccessableStep;
import io.flamingock.internal.core.task.executable.ExecutableTask;
import io.flamingock.internal.util.Result;

import java.util.List;
import java.util.stream.Collectors;

public abstract class FailedAfterExecutionAuditStep extends AfterExecutionAuditStep
        implements SuccessableStep, RollableFailedStep, FailedWithErrorStep {

    public static FailedAfterExecutionAuditStep fromFailedApply(ExecutableTask task, Throwable errorOnApply, Result auditResult) {
        if (auditResult instanceof Result.Error) {
            Result.Error errorResult = (Result.Error) auditResult;
            return new FailedExecutionFailedAuditStep(task, errorOnApply, errorResult.getError());
        } else {
            return new FailedExecutionSuccessAuditStep(task, errorOnApply);
        }
    }

    public static FailedAfterExecutionAuditStep fromSuccessApply(ExecutableTask task, Result.Error errorOnAudit) {
        return new SuccessExecutionFailedAuditStep(task, errorOnAudit.getError());
    }


    protected FailedAfterExecutionAuditStep(ExecutableTask task, boolean successAuditOperation) {
        super(task, successAuditOperation);
    }

    @Override
    public final RollableStep getRollbackStep() {
        return new RollableStep(getTask());
    }

}
