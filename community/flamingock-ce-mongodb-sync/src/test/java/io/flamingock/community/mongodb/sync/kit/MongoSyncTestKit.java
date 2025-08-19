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
package io.flamingock.community.mongodb.sync.kit;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import io.flamingock.core.kit.TestKit;
import io.flamingock.core.kit.TestFlamingockBuilder;
import io.flamingock.core.kit.audit.AuditTestHelper;
import io.flamingock.core.kit.lock.LockTestHelper;
import io.flamingock.community.mongodb.sync.driver.MongoSyncDriver;
import io.flamingock.targetystem.mongodb.sync.MongoSyncTargetSystem;

/**
 * MongoDB Sync implementation of TestKit for real database testing.
 * Provides unified API for creating builders and accessing test helpers
 * using actual MongoDB storage for both audit and lock operations.
 * 
 * This implementation creates helpers at construction time and provides
 * consistent access through getAuditHelper() and getLockHelper() methods,
 * identical to InMemoryTestKit API but backed by real MongoDB collections.
 */
public class MongoSyncTestKit implements TestKit {
    
    private final MongoClient mongoClient;
    private final MongoDatabase database;
    private final MongoSyncAuditStorage auditStorage;
    private final MongoSyncLockStorage lockStorage;
    private final AuditTestHelper auditHelper;
    private final LockTestHelper lockHelper;
    
    /**
     * Create a new MongoSyncTestKit with MongoDB client and database
     */
    public static MongoSyncTestKit create(MongoClient mongoClient, MongoDatabase database) {
        return new MongoSyncTestKit(mongoClient, database);
    }
    
    /**
     * Create a new MongoSyncTestKit with existing storage (useful for pre-conditions)
     */
    public static MongoSyncTestKit create(MongoClient mongoClient, MongoDatabase database, 
                                         MongoSyncAuditStorage auditStorage, MongoSyncLockStorage lockStorage) {
        return new MongoSyncTestKit(mongoClient, database, auditStorage, lockStorage);
    }
    
    private MongoSyncTestKit(MongoClient mongoClient, MongoDatabase database) {
        this.mongoClient = mongoClient;
        this.database = database;
        this.auditStorage = new MongoSyncAuditStorage(database);
        this.lockStorage = new MongoSyncLockStorage(database);
        
        // Create helpers at construction time - ready to use immediately
        this.auditHelper = new AuditTestHelper(auditStorage);
        this.lockHelper = new LockTestHelper(lockStorage);
    }
    
    private MongoSyncTestKit(MongoClient mongoClient, MongoDatabase database,
                            MongoSyncAuditStorage auditStorage, MongoSyncLockStorage lockStorage) {
        this.mongoClient = mongoClient;
        this.database = database;
        this.auditStorage = auditStorage;
        this.lockStorage = lockStorage;
        
        // Create helpers at construction time - ready to use immediately
        this.auditHelper = new AuditTestHelper(auditStorage);
        this.lockHelper = new LockTestHelper(lockStorage);
    }
    
    @Override
    public TestFlamingockBuilder createBuilder() {
        // Use real MongoDB driver from community module
        // Create target system and wrap it with real MongoSyncDriver
        MongoSyncTargetSystem targetSystem = new MongoSyncTargetSystem("test-target-system")
            .withMongoClient(mongoClient)
            .withDatabase(database);
        
        MongoSyncDriver driver = MongoSyncDriver.fromTargetSystem(targetSystem);
        
        // Create builder and add MongoDB dependencies for change injection
        TestFlamingockBuilder builder = createBuilderWithDriver(driver);
        builder.addDependency(mongoClient);
        builder.addDependency(database);
        return builder;
    }
    
    @Override
    public AuditTestHelper getAuditHelper() {
        return auditHelper;
    }
    
    @Override
    public LockTestHelper getLockHelper() {
        return lockHelper;
    }
    
    /**
     * Get direct access to audit storage for advanced testing scenarios
     */
    public MongoSyncAuditStorage getAuditStorage() {
        return auditStorage;
    }
    
    /**
     * Get direct access to lock storage for advanced testing scenarios
     */
    public MongoSyncLockStorage getLockStorage() {
        return lockStorage;
    }
    
    /**
     * Get access to MongoDB client for advanced scenarios
     */
    public MongoClient getMongoClient() {
        return mongoClient;
    }
    
    /**
     * Get access to MongoDB database for advanced scenarios
     */
    public MongoDatabase getDatabase() {
        return database;
    }
    
    /**
     * Clear all storage - useful between tests for clean state
     */
    public void clear() {
        auditStorage.clear();
        lockStorage.clear();
    }
}