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

import io.flamingock.internal.common.core.audit.AuditHistoryReader;
import io.flamingock.internal.common.core.audit.AuditReaderType;
import io.flamingock.internal.common.core.targets.OperationType;
import io.flamingock.internal.core.runtime.ExecutionRuntime;
import io.flamingock.internal.core.targets.AbstractTargetSystem;
import io.flamingock.internal.core.targets.TransactionalTargetSystem;
import io.flamingock.internal.core.targets.mark.TargetSystemAuditMark;

import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

public class TransactionalTargetSystemOpsImpl
        extends TargetSystemOpsImpl
        implements TransactionalTargetSystemOps {

    private final TransactionalTargetSystem<?> targetSystem;
    private final OperationType operationType;

    public TransactionalTargetSystemOpsImpl(TransactionalTargetSystem<?> targetSystem,
                                            AbstractTargetSystem<?> auditStoreTargetSystem) {
        super(targetSystem);
        this.targetSystem = targetSystem;
        this.operationType = internalGetOperationType(auditStoreTargetSystem);
    }

    @Override
    public OperationType getOperationType() {
        return operationType;
    }

    @Override
    public final <T> T applyChangeTransactional(Function<ExecutionRuntime, T> changeApplier, ExecutionRuntime executionRuntime) {
        executionRuntime.addContextLayer(targetSystem.getContext());
        return targetSystem.applyChangeTransactional(changeApplier, executionRuntime);
    }

    @Override
    public Optional<AuditHistoryReader> getAuditAuditReader(AuditReaderType type) {
        return targetSystem.getAuditAuditReader(type);
    }

    @Override
    public String getId() {
        return targetSystem.getId();
    }


    @Override
    public Set<TargetSystemAuditMark> listAll() {
        return targetSystem.getOnGoingTaskStatusRepository().listAll();
    }

    @Override
    public void clearMark(String changeId) {
        targetSystem.getOnGoingTaskStatusRepository().clearMark(changeId);
    }

    @Override
    public void mark(TargetSystemAuditMark auditMark) {
        targetSystem.getOnGoingTaskStatusRepository().mark(auditMark);
    }

    private OperationType internalGetOperationType(AbstractTargetSystem<?> auditStoreTargetSystem) {
        if (this.targetSystem.equals(auditStoreTargetSystem)) {
            return OperationType.TX_AUDIT_STORE_SHARED;
        } else if (this.targetSystem.hasMarker()) {
            return OperationType.TX_AUDIT_STORE_SYNC;
        } else {
            return OperationType.TX_NON_SYNC;
        }
    }
}
