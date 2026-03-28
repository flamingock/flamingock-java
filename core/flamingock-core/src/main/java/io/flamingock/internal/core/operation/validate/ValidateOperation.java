/*
 * Copyright 2026 Flamingock (https://www.flamingock.io)
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
package io.flamingock.internal.core.operation.validate;

import io.flamingock.internal.common.core.error.PendingChangesException;
import io.flamingock.internal.core.event.EventPublisher;
import io.flamingock.internal.core.operation.AbstractPipelineTraverseOperation;
import io.flamingock.internal.core.pipeline.execution.OrphanExecutionContext;
import io.flamingock.internal.core.pipeline.execution.StageExecutor;
import io.flamingock.internal.core.plan.ExecutionPlanner;
import io.flamingock.internal.util.id.RunnerId;

/**
 * Validates the pipeline without executing any changes.
 * If pending changes exist, throws {@link PendingChangesException}.
 */
public class ValidateOperation extends AbstractPipelineTraverseOperation {

    public ValidateOperation(RunnerId runnerId,
                             ExecutionPlanner executionPlanner,
                             StageExecutor stageExecutor,
                             OrphanExecutionContext orphanExecutionContext,
                             EventPublisher eventPublisher,
                             boolean throwExceptionIfCannotObtainLock,
                             Runnable finalizer) {
        super(runnerId, executionPlanner, stageExecutor, orphanExecutionContext, eventPublisher, throwExceptionIfCannotObtainLock, finalizer);
    }

    @Override
    protected boolean validateOnlyMode() {
        return true;
    }
}
