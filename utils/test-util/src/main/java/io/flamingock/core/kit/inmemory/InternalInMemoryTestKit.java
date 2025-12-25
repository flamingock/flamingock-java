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

import io.flamingock.core.kit.TestFlamingockBuilder;
import io.flamingock.core.kit.TestKit;
import io.flamingock.core.kit.audit.AuditTestHelper;
import io.flamingock.core.kit.lock.LockTestHelper;

/**
 * In-memory implementation of TestKit for core e2e testing.
 * 
 * <p>This implementation uses in-memory collections to store audit and lock data,
 * making it ideal for testing core Flamingock features without external dependencies.
 * It provides the same API as other TestKit implementations but with faster execution
 * and easier setup.</p>
 * 
 * <p><strong>Usage:</strong></p>
 * <pre>{@code
 * // Simple usage
 * InMemoryTestKit testKit = InMemoryTestKit.create();
 * testKit.createBuilder().build().run();
 * 
 * // With shared storage for multiple test runs
 * InMemoryAuditStorage auditStorage = new InMemoryAuditStorage();
 * InMemoryLockStorage lockStorage = new InMemoryLockStorage();
 * InMemoryTestKit testKit = InMemoryTestKit.create(auditStorage, lockStorage);
 * }</pre>
 * 
 * <p><strong>For other storage implementations:</strong> Use this as a reference
 * implementation when creating TestKits for MongoDB, DynamoDB, etc.</p>
 */
public class InternalInMemoryTestKit implements TestKit {
    
    private final InternalInMemoryAuditStorage auditStorage;
    private final InternalInMemoryLockStorage lockStorage;
    private final AuditTestHelper auditHelper;
    private final LockTestHelper lockHelper;

    public static InternalInMemoryTestKit create() {
        InternalInMemoryAuditStorage auditStorage = new InternalInMemoryAuditStorage();
        InternalInMemoryLockStorage lockStorage = new InternalInMemoryLockStorage();
        return new InternalInMemoryTestKit(auditStorage, lockStorage);
    }

    public static InternalInMemoryTestKit create(InternalInMemoryAuditStorage auditStorage, InternalInMemoryLockStorage lockStorage) {
        return new InternalInMemoryTestKit(auditStorage, lockStorage);
    }
    
    private InternalInMemoryTestKit(InternalInMemoryAuditStorage auditStorage, InternalInMemoryLockStorage lockStorage) {
        this.auditStorage = auditStorage;
        this.lockStorage = lockStorage;
        
        // Create helpers at construction time - ready to use immediately
        this.auditHelper = new AuditTestHelper(auditStorage);
        this.lockHelper = new LockTestHelper(lockStorage);
    }
    
    @Override
    public TestFlamingockBuilder createBuilder() {
        InternalInMemoryTestAuditStore auditStore = new InternalInMemoryTestAuditStore(auditStorage, lockStorage);
        return createBuilderWithAuditStore(auditStore);
    }
    
    @Override
    public AuditTestHelper getAuditHelper() {
        return auditHelper;
    }
    
    @Override
    public LockTestHelper getLockHelper() {
        return lockHelper;
    }

    @Override
    public void cleanUp() {
        auditStorage.clear();
        lockStorage.clear();
    }

    public InternalInMemoryAuditStorage getAuditStorage() {
        return auditStorage;
    }

    public InternalInMemoryLockStorage getLockStorage() {
        return lockStorage;
    }

    public void clear() {
        auditStorage.clear();
        lockStorage.clear();
    }
}