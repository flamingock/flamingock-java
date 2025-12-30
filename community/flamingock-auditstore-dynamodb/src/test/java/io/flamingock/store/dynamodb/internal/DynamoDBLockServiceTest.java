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
package io.flamingock.store.dynamodb.internal;


import io.flamingock.store.dynamodb.DynamoDBTestContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import io.flamingock.internal.util.constants.CommunityPersistenceConstants;
import io.flamingock.internal.core.store.lock.LockAcquisition;
import io.flamingock.internal.core.store.lock.LockKey;
import io.flamingock.internal.util.TimeService;
import io.flamingock.internal.util.id.RunnerId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

@Testcontainers
class DynamoDBLockServiceTest {

    private static final LockKey lockKey = LockKey.fromString("lockKey1");

    @Container
    static final GenericContainer<?> dynamoDBContainer = DynamoDBTestContainer.createContainer();

    private DynamoDbClient client;
    private DynamoDBLockService lockService;

    @BeforeEach
    void beforeEach() {
        client = DynamoDBTestContainer.createClient(dynamoDBContainer);
        
        // Clean up any existing tables to ensure test isolation
        cleanupTables();
        
        lockService = new DynamoDBLockService(client, new TimeService());
        lockService.initialize(true, CommunityPersistenceConstants.DEFAULT_LOCK_STORE_NAME, 5L, 5L);
    }
    
    private void cleanupTables() {
        try {
            // List and delete all tables to ensure clean state for each test
            java.util.List<String> tableNames = client.listTables().tableNames();
            for (String tableName : tableNames) {
                client.deleteTable(builder -> builder.tableName(tableName));
                // Wait for table deletion to complete
                client.waiter().waitUntilTableNotExists(builder -> builder.tableName(tableName));
            }
        } catch (Exception e) {
            // Ignore cleanup errors
        }
    }

    @AfterEach
    public void tearDown() {
        if (client != null) {
            client.close();
        }
    }

    @Test
    @DisplayName("Should acquire lock")
    public void shouldAcquire() {

        RunnerId runnerId = RunnerId.fromString("runner-1");
        LockAcquisition lockAcquisition = lockService.upsert(lockKey, runnerId, 10);

        Assertions.assertEquals(runnerId, lockAcquisition.getOwner());
        Assertions.assertEquals(10, lockAcquisition.getAcquiredForMillis());
    }

    @Test
    @DisplayName("Should acquire lock")
    public void shouldAcquireTwoDifferentKeys() {

        RunnerId runnerId = RunnerId.fromString("runner-1");
        LockAcquisition lockAcquisition = lockService.upsert(lockKey, runnerId, 10);

        Assertions.assertEquals(runnerId, lockAcquisition.getOwner());
        Assertions.assertEquals(10, lockAcquisition.getAcquiredForMillis());


        LockAcquisition lockAcquisition2 = lockService.upsert(LockKey.fromString("lockKey2"), runnerId, 20);

        Assertions.assertEquals(runnerId, lockAcquisition2.getOwner());
        Assertions.assertEquals(20, lockAcquisition2.getAcquiredForMillis());
    }

    @Test
    @DisplayName("Should allow acquiring again lock")
    public void shouldAllowAcquiringAgain() {

        RunnerId runnerId = RunnerId.fromString("runner-1");
        LockAcquisition lockAcquisition = lockService.upsert(lockKey, runnerId, 1000 * 10);

        Assertions.assertEquals(runnerId, lockAcquisition.getOwner());
        Assertions.assertEquals(10000, lockAcquisition.getAcquiredForMillis());

        LockAcquisition secondAcquisition = lockService.upsert(lockKey, runnerId, 2000 * 10);

        Assertions.assertEquals(runnerId, secondAcquisition.getOwner());
        Assertions.assertEquals(20000, secondAcquisition.getAcquiredForMillis());
    }

    @Test
    @DisplayName("Should extend lock")
    public void shouldExtend() {

        RunnerId runnerId = RunnerId.fromString("runner-1");
        LockAcquisition lockAcquisition = lockService.upsert(lockKey, runnerId, 1000 * 10);

        Assertions.assertEquals(runnerId, lockAcquisition.getOwner());
        Assertions.assertEquals(10000, lockAcquisition.getAcquiredForMillis());

        LockAcquisition lockExtension = lockService.extendLock(lockKey, runnerId, 1000 * 10);

        Assertions.assertEquals(runnerId, lockExtension.getOwner());
        Assertions.assertEquals(10000, lockExtension.getAcquiredForMillis());
    }

    @Test
    @DisplayName("Should not re-acquire if different owner")
    public void shouldNotAcquireIfDifferentOwner() {

        RunnerId runnerId = RunnerId.fromString("runner-1");
        LockAcquisition lockAcquisition = lockService.upsert(lockKey, runnerId, 1000 * 10);

        Assertions.assertEquals(runnerId, lockAcquisition.getOwner());
        Assertions.assertEquals(10000, lockAcquisition.getAcquiredForMillis());

        ConditionalCheckFailedException exception = Assertions.assertThrows(ConditionalCheckFailedException.class,
                () -> lockService.upsert(
                        lockKey,
                        RunnerId.fromString("runner-2"),
                        1000 * 10));

        Assertions.assertTrue(exception.getMessage().startsWith("The conditional request failed"));

    }

    @Test
    @DisplayName("Should allow re-acquiring if expired")
    public void shouldAllowReAcquiringIfExpired() throws InterruptedException {

        RunnerId runnerId = RunnerId.fromString("runner-1");
        LockAcquisition lockAcquisition = lockService.upsert(lockKey, runnerId, 1);

        Assertions.assertEquals(runnerId, lockAcquisition.getOwner());
        Assertions.assertEquals(1, lockAcquisition.getAcquiredForMillis());

        Thread.sleep(1);

        LockAcquisition secondAcquisition = lockService.upsert(lockKey, runnerId, 2000 * 10);

        Assertions.assertEquals(runnerId, secondAcquisition.getOwner());
        Assertions.assertEquals(20000, secondAcquisition.getAcquiredForMillis());
    }

    @Test
    @DisplayName("Should allow acquiring by other if expired")
    public void shouldAllowAcquiringByOtherIfExpired() throws InterruptedException {

        RunnerId runnerId = RunnerId.fromString("runner-1");
        LockAcquisition lockAcquisition = lockService.upsert(lockKey, runnerId, 1);

        Assertions.assertEquals(runnerId, lockAcquisition.getOwner());
        Assertions.assertEquals(1, lockAcquisition.getAcquiredForMillis());

        Thread.sleep(1);

        LockAcquisition secondAcquisition = lockService.upsert(lockKey, RunnerId.fromString("runner-2"), 2000 * 10);

        Assertions.assertEquals(RunnerId.fromString("runner-2"), secondAcquisition.getOwner());
        Assertions.assertEquals(20000, secondAcquisition.getAcquiredForMillis());
    }

    @Test
    @DisplayName("Should not extend if different owner")
    public void shouldNotExtendIfDifferentOwner() {

        RunnerId runnerId = RunnerId.fromString("runner-1");
        LockAcquisition lockAcquisition = lockService.upsert(lockKey, runnerId, 1000 * 10);

        Assertions.assertEquals(runnerId, lockAcquisition.getOwner());
        Assertions.assertEquals(10000, lockAcquisition.getAcquiredForMillis());

        ConditionalCheckFailedException exception = Assertions.assertThrows(ConditionalCheckFailedException.class,
                () -> lockService.extendLock(
                        lockKey,
                        RunnerId.fromString("runner-2"),
                        1000 * 10));

        Assertions.assertTrue(exception.getMessage().startsWith("The conditional request failed"));
    }


    @Test
    @DisplayName("Should not extend if expired")
    public void shouldNotExtendIfExpired() throws InterruptedException {

        RunnerId runnerId = RunnerId.fromString("runner-1");
        LockAcquisition lockAcquisition = lockService.upsert(lockKey, runnerId, 1);

        Assertions.assertEquals(runnerId, lockAcquisition.getOwner());
        Assertions.assertEquals(1, lockAcquisition.getAcquiredForMillis());

        Thread.sleep(1);

        ConditionalCheckFailedException exception = Assertions.assertThrows(ConditionalCheckFailedException.class,
                () -> lockService.extendLock(
                        lockKey,
                        runnerId,
                        1000 * 10));

        Assertions.assertTrue(exception.getMessage().startsWith("The conditional request failed"));
    }

}