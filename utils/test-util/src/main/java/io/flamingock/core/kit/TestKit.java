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

import io.flamingock.core.kit.audit.AuditTestHelper;
import io.flamingock.core.kit.lock.LockTestHelper;
import io.flamingock.internal.core.configuration.core.CoreConfiguration;
import io.flamingock.internal.core.configuration.community.CommunityConfiguration;
import io.flamingock.internal.core.store.CommunityAuditStore;
import io.flamingock.internal.core.context.SimpleContext;
import io.flamingock.internal.core.plugin.DefaultPluginManager;

/**
 * Unified testing interface for end-to-end Flamingock testing across different storage implementations.
 * 
 * <p>This interface provides a consistent API for testing core Flamingock features regardless of the 
 * underlying storage technology (InMemory, MongoDB, DynamoDB, etc.). Implementations should provide
 * the same testing capabilities while using their specific storage backend.</p>
 * 
 * <p><strong>Usage:</strong></p>
 * <pre>{@code
 * // InMemory testing
 * TestKit testKit = InMemoryTestKit.create();
 * 
 * // MongoDB testing  
 * TestKit testKit = MongoSyncTestKit.create(mongoClient, database);
 * 
 * // Same API for both
 * testKit.createBuilder().build().run();
 * testKit.getAuditHelper().verifySuccessfulChangeExecution("my-change");
 * }</pre>
 * 
 * <p><strong>Implementing for new storage types:</strong> Extend this interface and provide
 * storage-specific implementations of AuditStorage and LockStorage.</p>
 */
public interface TestKit {

    /**
     * Creates a TestFlamingockBuilder configured for testing with this storage implementation.
     * @return configured builder ready for test execution
     */
    TestFlamingockBuilder createBuilder();

    /**
     * Gets an audit helper for setting pre-conditions and verifying audit results.
     * @return audit helper backed by this kit's storage implementation
     */
    AuditTestHelper getAuditHelper();

    /**
     * Gets a lock helper for setting pre-conditions and verifying lock behavior.
     * @return lock helper backed by this kit's storage implementation
     */
    LockTestHelper getLockHelper();

    void cleanUp();


    default TestFlamingockBuilder createBuilderWithAuditStore(CommunityAuditStore auditStore) {
        return new TestFlamingockBuilder(
            new CoreConfiguration(),
            new CommunityConfiguration(), 
            new SimpleContext(),
            new DefaultPluginManager(),
            auditStore
        );
    }
}