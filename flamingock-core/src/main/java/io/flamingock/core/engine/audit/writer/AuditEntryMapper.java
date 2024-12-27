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

package io.flamingock.core.engine.audit.writer;

import io.flamingock.commons.utils.ThrowableUtil;
import io.flamingock.core.engine.audit.domain.AuditItem;
import io.flamingock.core.engine.audit.domain.RuntimeContext;
import io.flamingock.core.pipeline.execution.ExecutionContext;
import io.flamingock.core.task.descriptor.TaskDescriptor;

public final class AuditEntryMapper {

    private AuditEntryMapper() {
    }

    public static AuditEntry map(AuditItem auditItem) {
        TaskDescriptor taskDescriptor = auditItem.getTaskDescriptor();
        ExecutionContext stageExecutionContext = auditItem.getExecutionContext();
        RuntimeContext runtimeContext = auditItem.getRuntimeContext();
        return new AuditEntry(
                stageExecutionContext.getExecutionId(),
                runtimeContext.getStageName(),
                taskDescriptor.getId(),
                stageExecutionContext.getAuthor(),
                runtimeContext.getExecutedAt(),
                getAuditStatus(auditItem),
                getExecutionType(auditItem),
                taskDescriptor.getSourceName(),
                runtimeContext.getMethodExecutor(),
                runtimeContext.getDuration(),
                stageExecutionContext.getHostname(),
                stageExecutionContext.getMetadata(),
                getSystemChange(auditItem),
                ThrowableUtil.serialize(runtimeContext.getError().orElse(null))
        );
    }

    private static AuditEntry.Status getAuditStatus(AuditItem auditable) {
        switch (auditable.getOperation()) {
            case START_EXECUTION:
                return AuditEntry.Status.STARTED;
            case EXECUTION:
                return auditable.getRuntimeContext().isSuccess() ? AuditEntry.Status.EXECUTED : AuditEntry.Status.EXECUTION_FAILED;
            case ROLLBACK:
            default:
                return auditable.getRuntimeContext().isSuccess() ? AuditEntry.Status.ROLLED_BACK : AuditEntry.Status.ROLLBACK_FAILED;
        }
    }

    private static AuditEntry.ExecutionType getExecutionType(AuditItem auditItem) {
        return AuditEntry.ExecutionType.EXECUTION;
    }

    private static boolean getSystemChange(AuditItem auditItem) {
        return false;
    }

}
