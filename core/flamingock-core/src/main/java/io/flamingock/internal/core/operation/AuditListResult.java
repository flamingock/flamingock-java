package io.flamingock.internal.core.operation;

import io.flamingock.internal.common.core.audit.AuditEntry;

import java.util.List;

public class AuditListResult extends AbstractOperationResult {
    private final List<AuditEntry> auditEntries;

    public AuditListResult(List<AuditEntry> auditEntries) {
        this.auditEntries = auditEntries;
    }

    public List<AuditEntry> getAuditEntries() {
        return auditEntries;
    }
}
