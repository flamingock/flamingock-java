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

package io.flamingock.core.task.navigation.step.rolledback;

import io.flamingock.core.task.navigation.step.FailedStep;
import io.flamingock.core.task.navigation.step.SuccessableStep;
import io.flamingock.core.task.navigation.step.complete.failed.CompletedFailedManualRollback;
import io.flamingock.core.task.executable.Rollback;
import io.flamingock.commons.utils.Result;

public class ManualRolledBackStep extends RolledBackStep implements SuccessableStep, FailedStep {

    private final long duration;
    private final Rollback rollback;

    protected ManualRolledBackStep(Rollback rollback, boolean rollbackSuccess, long duration) {
        super(rollback.getTask(), rollbackSuccess);
        this.rollback = rollback;
        this.duration = duration;
    }

    public static ManualRolledBackStep successfulRollback(Rollback rollback, long duration) {
        return new ManualRolledBackStep(rollback, true, duration);
    }

    public static ManualRolledBackStep failedRollback(Rollback rollback, long duration, Throwable error) {
        return new FailedManualRolledBackStep(rollback, duration, error);
    }

    public CompletedFailedManualRollback applyAuditResult(Result auditResult) {
        return CompletedFailedManualRollback.fromRollbackAuditResult(this, auditResult);
    }

    public long getDuration() {
        return duration;
    }

    public Rollback getRollback() {
        return rollback;
    }
}
