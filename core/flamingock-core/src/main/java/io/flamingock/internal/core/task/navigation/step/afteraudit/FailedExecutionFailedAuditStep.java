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

import io.flamingock.internal.core.task.executable.ExecutableTask;
import io.flamingock.internal.core.task.navigation.step.FailedWithErrorStep;

public final class FailedExecutionFailedAuditStep extends FailedAfterExecutionAuditStep {

    private final Throwable errorOnApply;
    private final Throwable errorOnAudit;

    FailedExecutionFailedAuditStep(ExecutableTask task,
                                   Throwable errorOnApply,
                                   Throwable errorOnAudit) {
        super(task, true);
        this.errorOnApply = errorOnApply;
        this.errorOnAudit = errorOnAudit;
    }

    public Throwable getErrorOnApply() {
        return errorOnApply;
    }

    public Throwable getErrorOnAudit() {
        return getMainError();
    }

    @Override
    public Throwable getMainError() {
        return getErrorOnApply();
    }
}
