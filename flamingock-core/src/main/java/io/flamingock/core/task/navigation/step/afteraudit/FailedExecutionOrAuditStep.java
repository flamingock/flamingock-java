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

package io.flamingock.core.task.navigation.step.afteraudit;

import io.flamingock.core.task.navigation.step.RollableFailedStep;
import io.flamingock.core.task.navigation.step.SuccessableStep;
import io.flamingock.core.task.executable.ExecutableTask;
import io.flamingock.core.util.Result;

import java.util.List;
import java.util.stream.Collectors;

public abstract class FailedExecutionOrAuditStep extends AfterExecutionAuditStep
        implements SuccessableStep, RollableFailedStep {


    public static FailedExecutionOrAuditStep instance(ExecutableTask task, Result auditResult) {
        if (auditResult instanceof Result.Error) {
            Result.Error errorResult = (Result.Error) auditResult;
            return new FailedAuditExecutionStep(task, errorResult.getError());
        } else {
            return new FailedExecutionSuccessAuditStep(task);
        }
    }

    protected FailedExecutionOrAuditStep(ExecutableTask task, boolean successExecutionAudit) {
        super(task, successExecutionAudit);
    }


    @Override
    public final List<RollableStep> getRollbackSteps() {
        return task.getRollbackChain()
                .stream()
                .map(RollableStep::new)
                .collect(Collectors.toList());
    }

}
