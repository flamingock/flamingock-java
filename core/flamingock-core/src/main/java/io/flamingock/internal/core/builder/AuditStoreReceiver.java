package io.flamingock.internal.core.builder;


import io.flamingock.internal.core.store.CommunityAuditStore;

public interface AuditStoreReceiver<RETURN_TYPE> {

    RETURN_TYPE setAuditStore(CommunityAuditStore auditStore);
}
