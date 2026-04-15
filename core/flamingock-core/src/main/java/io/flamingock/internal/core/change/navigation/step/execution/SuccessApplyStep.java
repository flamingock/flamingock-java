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
package io.flamingock.internal.core.change.navigation.step.execution;

import io.flamingock.internal.core.change.executable.ExecutableChange;
import io.flamingock.internal.core.change.navigation.step.ExecutableStep;
import io.flamingock.internal.core.change.navigation.step.afteraudit.AfterExecutionAuditStep;
import io.flamingock.internal.core.change.navigation.step.afteraudit.FailedAfterExecutionAuditStep;
import io.flamingock.internal.core.change.navigation.step.complete.CompletedSuccessStep;
import io.flamingock.internal.util.Result;

public final class SuccessApplyStep extends ExecutionStep {
    public static SuccessApplyStep instance(ExecutableStep initialStep, long executionTimeMillis) {
        return new SuccessApplyStep(initialStep.getChange(), executionTimeMillis);
    }

    private SuccessApplyStep(ExecutableChange change, long executionTimeMillis) {
        super(change, true, executionTimeMillis);
    }

    @Override
    public AfterExecutionAuditStep withAuditResult(Result auditResult) {
        return auditResult.isError()
                ? FailedAfterExecutionAuditStep.fromSuccessApply(change, (Result.Error) auditResult)
                : CompletedSuccessStep.fromSuccessExecution(this);
    }
}
