package io.flamingock.internal.core.operation;

import io.flamingock.internal.core.external.store.audit.AuditPersistence;

public class AuditListOperation implements Operation<AuditListArgs, AuditListResult>{

    private final AuditPersistence persistence;

    public AuditListOperation(AuditPersistence persistence) {
        this.persistence = persistence;
    }

    @Override
    public AuditListResult execute(AuditListArgs args) {
        return new AuditListResult(persistence.getAuditHistory());
    }
}
