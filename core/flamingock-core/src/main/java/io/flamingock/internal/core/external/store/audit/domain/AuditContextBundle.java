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
package io.flamingock.internal.core.external.store.audit.domain;

import io.flamingock.internal.common.core.audit.AuditTxType;
import io.flamingock.internal.util.ThrowableUtil;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.targets.TargetSystemAuditMarkType;
import io.flamingock.internal.core.pipeline.execution.ExecutionContext;
import io.flamingock.internal.core.change.loaded.AbstractLoadedChange;
import io.flamingock.internal.core.change.loaded.AbstractTemplateLoadedChange;

import static io.flamingock.internal.common.core.audit.AuditEntry.ChangeType.MONGOCK_BEFORE;
import static io.flamingock.internal.common.core.audit.AuditEntry.ChangeType.MONGOCK_EXECUTION;
import static io.flamingock.internal.common.core.audit.AuditEntry.ChangeType.STANDARD_CODE;
import static io.flamingock.internal.common.core.audit.AuditEntry.ChangeType.STANDARD_TEMPLATE;

public abstract class AuditContextBundle {


    public enum Operation {

        START_EXECUTION, EXECUTION, ROLLBACK;

        private static final java.util.Map<Operation, TargetSystemAuditMarkType> TO_MARK_TYPE;
        private static final java.util.Map<TargetSystemAuditMarkType, Operation> FROM_MARK_TYPE;

        static {
            TO_MARK_TYPE = new java.util.EnumMap<>(Operation.class);
            TO_MARK_TYPE.put(EXECUTION, TargetSystemAuditMarkType.APPLIED);
            TO_MARK_TYPE.put(ROLLBACK, TargetSystemAuditMarkType.ROLLED_BACK);

            FROM_MARK_TYPE = new java.util.EnumMap<>(TargetSystemAuditMarkType.class);
            FROM_MARK_TYPE.put(TargetSystemAuditMarkType.APPLIED, EXECUTION);
            FROM_MARK_TYPE.put(TargetSystemAuditMarkType.ROLLED_BACK, ROLLBACK);
        }

        public TargetSystemAuditMarkType toOngoingStatusOperation() {
            TargetSystemAuditMarkType result = TO_MARK_TYPE.get(this);
            if (result == null) {
                throw new IllegalArgumentException("No TargetSystemAuditMarkType mapping for Operation." + this.name());
            }
            return result;
        }

        public static Operation fromOngoingStatusOperation(TargetSystemAuditMarkType ongoingOperation) {
            Operation result = FROM_MARK_TYPE.get(ongoingOperation);
            if (result == null) {
                throw new IllegalArgumentException("No Operation mapping for TargetSystemAuditMarkType." + ongoingOperation.name());
            }
            return result;
        }

    }

    private final String DEFAULT_AUTHOR = "not-specified";

    private final Operation operation;
    private final AbstractLoadedChange changeDescriptor;
    private final ExecutionContext executionContext;
    private final RuntimeContext runtimeContext;
    private final AuditTxType operationType;
    private final String targetSystemId;

    public AuditContextBundle(Operation operation,
                              AbstractLoadedChange changeDescriptor,
                              ExecutionContext executionContext,
                              RuntimeContext runtimeContext,
                              AuditTxType auditTxType,
                              String targetSystemId) {
        this.operation = operation;
        this.changeDescriptor = changeDescriptor;
        this.executionContext = executionContext;
        this.runtimeContext = runtimeContext;
        this.operationType = auditTxType;
        this.targetSystemId = targetSystemId;
    }

    public Operation getOperation() {
        return operation;
    }

    public AbstractLoadedChange getChangeDescriptor() {
        return changeDescriptor;
    }

    public ExecutionContext getExecutionContext() {
        return executionContext;
    }

    public RuntimeContext getRuntimeContext() {
        return runtimeContext;
    }

    public AuditTxType getAuditTxType() {
        return operationType;
    }

    public String getTargetSystemId() {
        return targetSystemId;
    }


    public AuditEntry toAuditEntry() {
        AbstractLoadedChange loadedChange = getChangeDescriptor();
        ExecutionContext stageExecutionContext = getExecutionContext();
        RuntimeContext runtimeContext = getRuntimeContext();
        String author = loadedChange.getAuthor() != null ? loadedChange.getAuthor() : DEFAULT_AUTHOR;
        return new AuditEntry(
                stageExecutionContext.getExecutionId(),
                runtimeContext.getStageName(),
                loadedChange.getId(),
                author,
                runtimeContext.getAppliedAt(),
                getAuditStatus(),
                getChangeType(),
                loadedChange.getSource(),
                runtimeContext.getMethodExecutor(),
                loadedChange.getSourceFile(),
                runtimeContext.getDuration(),
                stageExecutionContext.getHostname(),
                stageExecutionContext.getMetadata(),
                loadedChange.isSystem(),
                ThrowableUtil.serialize(runtimeContext.getError().orElse(null)),
                getAuditTxType(),
                getTargetSystemId(),
                loadedChange.getOrder().orElseThrow(() -> new IllegalStateException("Order is required but not present")),
                loadedChange.getRecovery().getStrategy(),
                loadedChange.isTransactional()
        );
    }

    private AuditEntry.Status getAuditStatus() {
        switch (getOperation()) {
            case START_EXECUTION:
                return AuditEntry.Status.STARTED;
            case EXECUTION:
                return getRuntimeContext().isSuccess() ? AuditEntry.Status.APPLIED : AuditEntry.Status.FAILED;
            case ROLLBACK:
            default:
                return getRuntimeContext().isSuccess() ? AuditEntry.Status.ROLLED_BACK : AuditEntry.Status.ROLLBACK_FAILED;
        }
    }

    private AuditEntry.ChangeType getChangeType() {
        if(changeDescriptor.isLegacy()) {
            return changeDescriptor.getId().endsWith("_before")
                    ? MONGOCK_BEFORE
                    : MONGOCK_EXECUTION;
        } else if(changeDescriptor instanceof AbstractTemplateLoadedChange) {
            return STANDARD_TEMPLATE;
        } else {
            return STANDARD_CODE;
        }
    }



}