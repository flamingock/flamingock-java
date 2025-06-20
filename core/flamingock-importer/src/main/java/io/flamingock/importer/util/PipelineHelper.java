package io.flamingock.importer.util;

import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.pipeline.PipelineDescriptor;
import org.jetbrains.annotations.NotNull;

public class PipelineHelper {
    private static final String errorTemplate = "importing changeUnit with id[%s] from database. It must be imported  to a flamingock stage";

    private final PipelineDescriptor pipelineDescriptor;

    public PipelineHelper(PipelineDescriptor pipelineDescriptor) {
        this.pipelineDescriptor = pipelineDescriptor;
    }

    public String getStageId(AuditEntry auditEntryFromOrigin) {
        if (Boolean.TRUE.equals(auditEntryFromOrigin.getSystemChange())) {
            return "mongock-legacy-system-changes";
        } else {
            String taskIdInPipeline = getBaseTaskId(auditEntryFromOrigin);
            return pipelineDescriptor.getStageByTask(taskIdInPipeline).orElseThrow(() -> generateTaskIdException(taskIdInPipeline));
        }
    }



    public String getBaseTaskId(AuditEntry auditEntry) {
        String originalTaskId = auditEntry.getTaskId();
        int index = originalTaskId.indexOf("_before");
        return index >= 0 ? originalTaskId.substring(0, index) : originalTaskId;
    }

    public String getStorableTaskId(AuditEntry auditEntry) {
        return auditEntry.getTaskId();
    }

    @NotNull
    public IllegalArgumentException generateTaskIdException(String taskIdInPipeline) {
        return new IllegalArgumentException(String.format(errorTemplate, taskIdInPipeline));
    }
}
