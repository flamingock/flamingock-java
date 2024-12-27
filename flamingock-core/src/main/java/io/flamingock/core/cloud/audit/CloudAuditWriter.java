package io.flamingock.core.cloud.audit;

import io.flamingock.commons.utils.Result;
import io.flamingock.core.engine.audit.AuditWriter;
import io.flamingock.core.engine.audit.domain.ExecutionAuditItem;
import io.flamingock.core.engine.audit.domain.RollbackAuditItem;
import io.flamingock.core.engine.audit.domain.StartExecutionAuditItem;
import io.flamingock.core.engine.audit.writer.AuditEntryMapper;

public interface CloudAuditWriter extends AuditWriter {

    default Result writeStartExecution(StartExecutionAuditItem auditItem) {
        return Result.OK();//TODO remove this
//        return writeEntry(AuditEntryMapper.map(auditItem));
    }


    default Result writeExecution(ExecutionAuditItem auditItem) {
        return writeEntry(AuditEntryMapper.map(auditItem));
    }

    default Result writeRollback(RollbackAuditItem auditItem) {
        return writeEntry(AuditEntryMapper.map(auditItem));
    }
}
