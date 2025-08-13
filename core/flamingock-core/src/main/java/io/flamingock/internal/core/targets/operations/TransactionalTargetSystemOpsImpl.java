/*
 * Copyright 2025 Flamingock (https://www.flamingock.io)
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
package io.flamingock.internal.core.targets.operations;

import io.flamingock.internal.common.core.context.ContextResolver;
import io.flamingock.internal.common.core.targets.OperationType;
import io.flamingock.internal.core.runtime.ExecutionRuntime;
import io.flamingock.internal.core.targets.mark.TargetSystemAuditMark;
import io.flamingock.internal.core.targets.TransactionalTargetSystem;

import java.util.Set;
import java.util.function.Function;

public class TransactionalTargetSystemOpsImpl
        extends TargetSystemOpsImpl
        implements TransactionalTargetSystemOps {

    private final TransactionalTargetSystem<?> transactionalTargetSystem;
    private final OperationType operationType;

    public TransactionalTargetSystemOpsImpl(TransactionalTargetSystem<?> transactionalTargetSystem,
                                            boolean sameAuditStoreTxResource) {
        super(transactionalTargetSystem);
        this.transactionalTargetSystem = transactionalTargetSystem;
        this.operationType = internalGetOperationType(sameAuditStoreTxResource);
    }

    @Override
    public OperationType getOperationType() {
        return operationType;
    }

    @Override
    public <T> T applyChange(Function<ExecutionRuntime, T> changeApplier, ExecutionRuntime executionRuntime) {
        return transactionalTargetSystem.applyChange(changeApplier, executionRuntime);
    }

    @Override
    public <T> T applyChangeTransactional(Function<ExecutionRuntime, T> changeApplier, ExecutionRuntime executionRuntime) {
        return transactionalTargetSystem.applyChangeTransactional(changeApplier, executionRuntime);
    }

    @Override
    public String getId() {
        return transactionalTargetSystem.getId();
    }

    @Override
    public ContextResolver decorateOnTop(ContextResolver baseContext) {
        return transactionalTargetSystem.decorateOnTop(baseContext);
    }

    @Override
    public Set<TargetSystemAuditMark> listAll() {
        return transactionalTargetSystem.getOnGoingTaskStatusRepository().listAll();
    }

    @Override
    public void clear(String changeId) {
        transactionalTargetSystem.getOnGoingTaskStatusRepository().clear(changeId);
    }

    @Override
    public void mark(TargetSystemAuditMark auditMark) {
        transactionalTargetSystem.getOnGoingTaskStatusRepository().mark(auditMark);
    }

    private OperationType internalGetOperationType(boolean sameAuditStoreTxResource) {
        if (sameAuditStoreTxResource) {
            return OperationType.TX_AUDIT_STORE_SHARED;
        } else if (transactionalTargetSystem.inSyncWithAuditStore()) {
            return OperationType.TX_AUDIT_STORE_SYNC;
        } else {
            return OperationType.TX_NON_SYNC;
        }
    }
}
