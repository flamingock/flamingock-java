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
package io.flamingock.internal.core.change.navigation.step.rolledback;

import io.flamingock.internal.core.change.executable.ExecutableChange;
import io.flamingock.internal.core.change.navigation.step.FailedStep;
import io.flamingock.internal.core.change.navigation.step.SuccessableStep;
import io.flamingock.internal.core.change.navigation.step.complete.failed.CompletedFailedManualRollback;
import io.flamingock.internal.util.Result;

public class ManualRolledBackStep extends RolledBackStep implements SuccessableStep, FailedStep {

    private final long duration;

    protected ManualRolledBackStep(ExecutableChange executableChange, boolean rollbackSuccess, long duration) {
        super(executableChange, rollbackSuccess);
        this.duration = duration;
    }

    public static ManualRolledBackStep successfulRollback(ExecutableChange executableChange, long duration) {
        return new ManualRolledBackStep(executableChange, true, duration);
    }

    public static ManualRolledBackStep failedRollback(ExecutableChange executableChange, long duration, Throwable error) {
        return new FailedManualRolledBackStep(executableChange, duration, error);
    }

    public CompletedFailedManualRollback applyAuditResult(Result auditResult) {
        return CompletedFailedManualRollback.fromRollbackAuditResult(this, auditResult);
    }

    public long getDuration() {
        return duration;
    }

}
