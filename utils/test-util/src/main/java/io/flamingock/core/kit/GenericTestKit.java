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
package io.flamingock.core.kit;

import io.flamingock.core.kit.audit.AuditStorage;
import io.flamingock.core.kit.audit.AuditTestHelper;
import io.flamingock.core.kit.lock.LockStorage;
import io.flamingock.core.kit.lock.LockTestHelper;
import io.flamingock.internal.core.community.driver.LocalDriver;

/**
 * Generic TestKit implementation that works with any storage technology.
 * Accepts audit storage, lock storage, and driver implementations from the test user.
 * 
 * This is the preferred implementation for testing with real databases as it follows
 * the proper dependency separation - storage classes only depend on their respective
 * database clients, not on Flamingock-specific database components.
 */
public class GenericTestKit implements TestKit {
    
    private final AuditStorage auditStorage;
    private final LockStorage lockStorage;
    private final LocalDriver driver;
    private final AuditTestHelper auditHelper;
    private final LockTestHelper lockHelper;
    
    public GenericTestKit(AuditStorage auditStorage, LockStorage lockStorage, LocalDriver driver) {
        this.auditStorage = auditStorage;
        this.lockStorage = lockStorage;
        this.driver = driver;
        
        // Create helpers at construction time - ready to use immediately
        this.auditHelper = new AuditTestHelper(auditStorage);
        this.lockHelper = new LockTestHelper(lockStorage);
    }
    
    @Override
    public TestFlamingockBuilder createBuilder() {
        return createBuilderWithDriver(driver);
    }
    
    @Override
    public AuditTestHelper getAuditHelper() {
        return auditHelper;
    }
    
    @Override
    public LockTestHelper getLockHelper() {
        return lockHelper;
    }
}