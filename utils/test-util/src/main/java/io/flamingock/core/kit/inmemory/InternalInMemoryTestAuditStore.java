/*
 * Copyright 2025 Flamingock (https://www.flamingock.io)
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
package io.flamingock.core.kit.inmemory;

import io.flamingock.internal.common.core.context.ContextResolver;
import io.flamingock.internal.core.external.store.lock.community.CommunityLockService;
import io.flamingock.internal.core.external.store.CommunityAuditStore;
import io.flamingock.internal.util.Constants;
import io.flamingock.internal.util.id.RunnerId;

public class InternalInMemoryTestAuditStore implements CommunityAuditStore {
    
    private final InternalInMemoryAuditStorage auditStorage;
    private final InternalInMemoryLockStorage lockStorage;
    private InternalInMemoryTestAuditPersistence persistence;
    private RunnerId runnerId;

    public InternalInMemoryTestAuditStore(InternalInMemoryAuditStorage auditStorage, InternalInMemoryLockStorage lockStorage) {
        this.auditStorage = auditStorage;
        this.lockStorage = lockStorage;
    }

    @Override
    public String getId() {
        return Constants.DEFAULT_IN_MEMORY_AUDIT_STORE;
    }
    
    @Override
    public void initialize(ContextResolver contextResolver) {
        // Extract required components from context
        runnerId = contextResolver.getRequiredDependencyValue(RunnerId.class);
        // Create the test audit persistence with domain-separated storage
        this.persistence = new InternalInMemoryTestAuditPersistence(auditStorage);
    }

    
    @Override
    public InternalInMemoryTestAuditPersistence getPersistence() {
        if (persistence == null) {
            throw new IllegalStateException("AuditStore not initialized - call initialize first");
        }
        return persistence;
    }

    @Override
    public CommunityLockService getLockService() {
        return new InternalInMemoryLockService(lockStorage, runnerId);
    }
}