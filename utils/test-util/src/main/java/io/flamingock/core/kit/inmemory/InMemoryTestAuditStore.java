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
import io.flamingock.internal.core.community.store.LocalAuditStore;
import io.flamingock.internal.core.builder.core.CoreConfigurable;
import io.flamingock.internal.util.id.RunnerId;

public class InMemoryTestAuditStore implements LocalAuditStore {
    
    private final InMemoryAuditStorage auditStorage;
    private final InMemoryLockStorage lockStorage;
    private InMemoryTestEngine engine;

    public InMemoryTestAuditStore(InMemoryAuditStorage auditStorage, InMemoryLockStorage lockStorage) {
        this.auditStorage = auditStorage;
        this.lockStorage = lockStorage;
    }
    
    @Override
    public void initialize(ContextResolver contextResolver) {
        // Extract required components from context
        RunnerId instanceId = contextResolver.getRequiredDependencyValue(RunnerId.class);
        CoreConfigurable coreConfiguration = contextResolver.getRequiredDependencyValue(CoreConfigurable.class);
        
        // Create the test connection engine with domain-separated storage
        this.engine = new InMemoryTestEngine(auditStorage, lockStorage, instanceId, coreConfiguration);
    }
    
    @Override
    public boolean isCloud() {
        return false;
    }
    
    @Override
    public InMemoryTestEngine getEngine() {
        if (engine == null) {
            throw new IllegalStateException("AuditStore not initialized - call initialize first");
        }
        return engine;
    }
}