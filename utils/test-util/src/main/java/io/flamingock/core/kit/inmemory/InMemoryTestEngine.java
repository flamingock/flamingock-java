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

import io.flamingock.core.kit.audit.AuditStorage;
import io.flamingock.core.kit.lock.LockStorage;
import io.flamingock.core.kit.audit.TestAuditReader;
import io.flamingock.core.kit.audit.TestAuditWriter;
import io.flamingock.internal.core.builder.core.CoreConfigurable;
import io.flamingock.internal.core.community.LocalEngine;
import io.flamingock.internal.core.community.LocalExecutionPlanner;
import io.flamingock.internal.core.engine.audit.ExecutionAuditWriter;
import io.flamingock.internal.core.engine.execution.ExecutionPlanner;
import io.flamingock.internal.core.transaction.TransactionWrapper;
import io.flamingock.internal.util.id.RunnerId;

import java.util.Optional;


public class InMemoryTestEngine implements LocalEngine {
    
    private final TestAuditWriter auditWriter;
    private final TestAuditReader auditReader;
    private final InMemoryLockService lockService;
    private final LocalExecutionPlanner executionPlanner;
    
    public InMemoryTestEngine(AuditStorage auditStorage,
                              LockStorage lockStorage,
                              RunnerId instanceId,
                              CoreConfigurable coreConfiguration) {
        
        this.auditWriter = new TestAuditWriter(auditStorage);
        this.auditReader = new TestAuditReader(auditStorage);
        this.lockService = new InMemoryLockService(lockStorage, instanceId);
        this.executionPlanner = new LocalExecutionPlanner(instanceId, lockService, auditReader, coreConfiguration);
    }
    
    @Override
    public ExecutionAuditWriter getAuditWriter() {
        return auditWriter;
    }
    
    @Override
    public ExecutionPlanner getExecutionPlanner() {
        return executionPlanner;
    }
    
    @Override
    public Optional<? extends TransactionWrapper> getTransactionWrapper() {
        return Optional.empty();
    }
    
}