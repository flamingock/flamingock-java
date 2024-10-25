package io.flamingock.oss.driver.dynamodb.internal;


import com.amazonaws.services.dynamodbv2.local.main.ServerRunner;
import com.amazonaws.services.dynamodbv2.local.server.DynamoDBProxyServer;
import io.flamingock.commons.utils.RunnerId;
import io.flamingock.commons.utils.TimeService;
import io.flamingock.core.engine.lock.LockAcquisition;
import io.flamingock.core.engine.lock.LockKey;
import io.flamingock.oss.driver.dynamodb.internal.util.DynamoClients;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

import java.net.URI;

class DynamoDBLockServiceTest {

    private static final LockKey lockKey = LockKey.fromString("lockKey1");

    private static DynamoDBProxyServer dynamoDBLocal;
    private static DynamoDbClient client;

    DynamoDBLockService lockService;

    @BeforeEach
    void beforeEach() throws Exception {
        dynamoDBLocal = ServerRunner.createServerFromCommandLineArgs(new String[]{"-inMemory", "-port", "8000"});
        dynamoDBLocal.start();

        client = DynamoDbClient.builder().region(Region.EU_WEST_1).endpointOverride(new URI("http://localhost:8000")).credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("dummye", "dummye"))).build();

        lockService = new DynamoDBLockService(new DynamoClients(client), new TimeService());
        lockService.initialize(true);

    }

    @AfterEach
    public void tearDown() throws Exception {
        dynamoDBLocal.stop();
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