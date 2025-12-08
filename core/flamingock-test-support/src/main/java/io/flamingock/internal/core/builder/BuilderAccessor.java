package io.flamingock.internal.core.builder;

import io.flamingock.internal.core.store.AuditStore;

public class BuilderAccessor {

    private AbstractChangeRunnerBuilder<?, ?> builder;

    public BuilderAccessor(AbstractChangeRunnerBuilder<?,?> builder) {
        this.builder = builder;
    }

    public AuditStore<?> getAuditStore(){
        return builder.auditStore;
    }
}
