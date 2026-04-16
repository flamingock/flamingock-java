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

import io.flamingock.internal.core.change.navigation.step.AbstractChangeStep;
import io.flamingock.internal.core.change.navigation.step.SuccessableStep;
import io.flamingock.internal.core.change.navigation.step.afteraudit.AfterExecutionAuditStep;
import io.flamingock.internal.core.change.executable.ExecutableChange;
import io.flamingock.internal.util.Result;

public abstract class ExecutionStep extends AbstractChangeStep implements SuccessableStep {

    private final long duration;
    private final boolean successExecution;

    protected ExecutionStep(ExecutableChange change, boolean successExecution, long duration) {
        super(change);
        this.successExecution = successExecution;
        this.duration = duration;
    }

    public long getDuration() {
        return duration;
    }

    public abstract AfterExecutionAuditStep withAuditResult(Result saveResult);

    @Override
    public final boolean isSuccessStep() {
        return successExecution;
    }

}
