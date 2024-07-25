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

package io.flamingock.core.task.navigation.step.complete.failed;

import io.flamingock.core.task.navigation.step.AbstractTaskStep;
import io.flamingock.core.task.navigation.step.FailedStep;
import io.flamingock.core.task.navigation.step.SuccessableStep;
import io.flamingock.core.task.navigation.step.rolledback.ManualRolledBackStep;
import io.flamingock.core.task.executable.ExecutableTask;
import io.flamingock.core.util.Result;

public class CompletedFailedManualRollback extends AbstractTaskStep implements SuccessableStep, FailedStep {

    public static CompletedFailedManualRollback fromRollbackAuditResult(ManualRolledBackStep rolledBack, Result auditResult) {
        return auditResult instanceof Result.Error
                ? new CompletedFailedAtRollbackAuditStep(rolledBack, ((Result.Error) auditResult).getError())
                : new CompletedFailedManualRollback(rolledBack.getTask());
    }

    protected CompletedFailedManualRollback(ExecutableTask task) {
        super(task);
    }

    @Override
    public boolean isSuccessStep() {
        return true;
    }


}
