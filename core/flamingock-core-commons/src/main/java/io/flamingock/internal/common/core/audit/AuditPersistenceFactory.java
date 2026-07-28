package io.flamingock.internal.common.core.audit;

public interface AuditPersistenceFactory<PERSISTENCE extends AuditPersistence> {

    PERSISTENCE get(String stageId);


}
