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
package io.flamingock.internal.core.change.navigation.step.complete.failed;

import io.flamingock.internal.core.change.navigation.step.AbstractChangeStep;
import io.flamingock.internal.core.change.navigation.step.FailedStep;
import io.flamingock.internal.core.change.navigation.step.SuccessableStep;
import io.flamingock.internal.core.change.navigation.step.rolledback.ManualRolledBackStep;
import io.flamingock.internal.core.change.executable.ExecutableChange;
import io.flamingock.internal.util.Result;

public class CompletedFailedManualRollback extends AbstractChangeStep implements SuccessableStep, FailedStep {

    public static CompletedFailedManualRollback fromRollbackAuditResult(ManualRolledBackStep rolledBack, Result auditResult) {
        return auditResult instanceof Result.Error
                ? new CompletedFailedAtRollbackAuditStep(rolledBack, ((Result.Error) auditResult).getError())
                : new CompletedFailedManualRollback(rolledBack.getChange());
    }

    protected CompletedFailedManualRollback(ExecutableChange change) {
        super(change);
    }

    @Override
    public boolean isSuccessStep() {
        return true;
    }


}
