package io.flamingock.cloud.audit;

import io.flamingock.commons.utils.Result;
import io.flamingock.internal.core.engine.audit.ExecutionAuditWriter;
import io.flamingock.internal.core.engine.audit.domain.ExecutionAuditContextBundle;
import io.flamingock.internal.core.engine.audit.domain.RollbackAuditContextBundle;
import io.flamingock.internal.core.engine.audit.domain.StartExecutionAuditContextBundle;

public interface CloudAuditWriter extends ExecutionAuditWriter {

    default Result writeStartExecution(StartExecutionAuditContextBundle auditContextBundle) {
        return Result.OK();//TODO remove this
//        return writeEntry(AuditEntryMapper.map(auditContextBundle));
    }


    default Result writeExecution(ExecutionAuditContextBundle auditContextBundle) {
        return writeEntry(auditContextBundle.toAuditEntry());
    }

    default Result writeRollback(RollbackAuditContextBundle auditContextBundle) {
        return writeEntry(auditContextBundle.toAuditEntry());
    }
}
