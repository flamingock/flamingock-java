/*
 * Copyright 2023 Flamingock (https://www.flamingock.io)
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
package io.flamingock.community.mongodb.sync;

import com.mongodb.ReadConcernLevel;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import io.flamingock.common.test.pipeline.CodeChangeUnitTestDefinition;
import io.flamingock.community.mongodb.sync.changes._001_create_client_collection_happy;
import io.flamingock.community.mongodb.sync.changes._002_insert_federico_happy_non_transactional;
import io.flamingock.community.mongodb.sync.changes._002_insert_federico_happy_transactional;
import io.flamingock.community.mongodb.sync.changes._003_insert_jorge_failed_transactional_non_rollback;
import io.flamingock.community.mongodb.sync.changes._003_insert_jorge_happy_transactional;
import io.flamingock.community.mongodb.sync.driver.MongoSyncDriver;
import io.flamingock.core.kit.TestKit;
import io.flamingock.core.kit.audit.AuditTestHelper;
import io.flamingock.core.kit.audit.AuditTestSupport;
import io.flamingock.internal.core.runner.PipelineExecutionException;
import io.flamingock.mongodb.kit.MongoSyncAuditStorage;
import io.flamingock.mongodb.kit.MongoSyncLockStorage;
import io.flamingock.mongodb.kit.MongoSyncTestKit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static io.flamingock.core.kit.audit.AuditEntryExpectation.EXECUTED;
import static io.flamingock.core.kit.audit.AuditEntryExpectation.EXECUTION_FAILED;
import static io.flamingock.core.kit.audit.AuditEntryExpectation.ROLLED_BACK;
import static io.flamingock.core.kit.audit.AuditEntryExpectation.STARTED;
import static io.flamingock.internal.core.community.Constants.DEFAULT_AUDIT_STORE_NAME;
import static io.flamingock.internal.core.community.Constants.DEFAULT_LOCK_STORE_NAME;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class MongoSyncDriverTest {

    private static final String DB_NAME = "test";

    private static final String CLIENTS_COLLECTION = "clientCollection";

    private static final String CUSTOM_AUDIT_REPOSITORY_NAME = "testFlamingockAudit";
    private static final String CUSTOM_LOCK_REPOSITORY_NAME = "testFlamingockLock";

    private static MongoClient mongoClient;

    private static MongoDatabase database;

    @Deprecated
    private static MongoDBTestHelper mongoDBTestHelper;


    @Container
    public static final MongoDBContainer mongoDBContainer = new MongoDBContainer(DockerImageName.parse("mongo:6"))
            .withReuse(true);
    private TestKit testKit;
    private AuditTestHelper auditHelper;


    @BeforeEach
    void setupEach() {
        mongoClient = MongoClients.create(mongoDBContainer.getConnectionString());
        database = mongoClient.getDatabase("test");
        testKit = MongoSyncTestKit.create(new MongoSyncDriver(), mongoClient, database);
        auditHelper = testKit.getAuditHelper();

        mongoDBTestHelper = new MongoDBTestHelper(database);

    }

    @AfterEach
    void tearDown() {
        database.drop(); // Clean between tests
        mongoClient.close();
    }

    @Test
    @DisplayName("When standalone runs the driver with DEFAULT repository names related collections should exists")
    void happyPathWithDefaultRepositoryNames() {
        //Given-When-Then
        AuditTestSupport.withTestKit(testKit)
                .GIVEN_ChangeUnits(
                        new CodeChangeUnitTestDefinition(_001_create_client_collection_happy.class, Collections.singletonList(MongoDatabase.class)),
                        new CodeChangeUnitTestDefinition(_002_insert_federico_happy_transactional.class, Arrays.asList(MongoDatabase.class, ClientSession.class)),
                        new CodeChangeUnitTestDefinition(_003_insert_jorge_happy_transactional.class, Arrays.asList(MongoDatabase.class, ClientSession.class))
                )
                .WHEN(() -> testKit.createBuilder()
                        .setRelaxTargetSystemValidation(true)
                        .addDependency(mongoClient)
                        .addDependency(database)
                        .build()
                        .run())
                .THEN_VerifyAuditSequenceStrict(
                        STARTED("create-client-collection"),
                        EXECUTED("create-client-collection"),
                        STARTED("insert-federico-document"),
                        EXECUTED("insert-federico-document"),
                        STARTED("insert-jorge-document"),
                        EXECUTED("insert-jorge-document")
                )
                .run();

        assertTrue(mongoDBTestHelper.collectionExists(DEFAULT_AUDIT_STORE_NAME));
        assertTrue(mongoDBTestHelper.collectionExists(DEFAULT_LOCK_STORE_NAME));
        assertFalse(mongoDBTestHelper.collectionExists(CUSTOM_AUDIT_REPOSITORY_NAME));
        assertFalse(mongoDBTestHelper.collectionExists(CUSTOM_LOCK_REPOSITORY_NAME));
    }

    @Test
    @DisplayName("When standalone runs the driver with CUSTOM config properties all properties are correctly set")
    void happyPathWithCustomConfigOptions() {
        //Given-When-Then
        MongoDBSyncConfiguration config = new MongoDBSyncConfiguration();

        // Create a custom TestKit that uses the custom audit collection name
        MongoSyncAuditStorage customAuditStorage = new MongoSyncAuditStorage(database, CUSTOM_AUDIT_REPOSITORY_NAME);
        MongoSyncLockStorage customLockStorage = new MongoSyncLockStorage(database, CUSTOM_LOCK_REPOSITORY_NAME);
        TestKit customTestKit = new MongoSyncTestKit(customAuditStorage, customLockStorage, new MongoSyncDriver(), mongoClient);

        AuditTestSupport.withTestKit(customTestKit)
                .GIVEN_ChangeUnits(
                        new CodeChangeUnitTestDefinition(_001_create_client_collection_happy.class, Collections.singletonList(MongoDatabase.class)),
                        new CodeChangeUnitTestDefinition(_002_insert_federico_happy_transactional.class, Arrays.asList(MongoDatabase.class, ClientSession.class)),
                        new CodeChangeUnitTestDefinition(_003_insert_jorge_happy_transactional.class, Arrays.asList(MongoDatabase.class, ClientSession.class))
                )
                .WHEN(() -> testKit.createBuilder()
                        .setRelaxTargetSystemValidation(true)
                        .addDependency(mongoClient)
                        .addDependency(database)
                        .addDependency(config)
                        .setProperty("mongodb.indexCreation", true)
                        .setProperty("mongodb.auditRepositoryName", CUSTOM_AUDIT_REPOSITORY_NAME)
                        .setProperty("mongodb.lockRepositoryName", CUSTOM_LOCK_REPOSITORY_NAME)
                        .setProperty("mongodb.readConcern", "LOCAL")
                        .setProperty("mongodb.readPreference", MongoDBSyncConfiguration.ReadPreferenceLevel.SECONDARY)
                        .build()
                        .run())
                .THEN_VerifyAuditSequenceStrict(
                        STARTED("create-client-collection"),
                        EXECUTED("create-client-collection"),
                        STARTED("insert-federico-document"),
                        EXECUTED("insert-federico-document"),
                        STARTED("insert-jorge-document"),
                        EXECUTED("insert-jorge-document")
                )
                .run();

        assertTrue(config.isAutoCreate());
        //TODO: assertFalse(config.isIndexCreation());
        assertEquals(CUSTOM_AUDIT_REPOSITORY_NAME, config.getAuditRepositoryName());
        assertEquals(CUSTOM_LOCK_REPOSITORY_NAME, config.getLockRepositoryName());
        assertEquals(ReadConcernLevel.LOCAL, config.getReadConcern());
        assertEquals(MongoDBSyncConfiguration.ReadPreferenceLevel.SECONDARY, config.getReadPreference());

        assertFalse(mongoDBTestHelper.collectionExists(DEFAULT_AUDIT_STORE_NAME));
        assertFalse(mongoDBTestHelper.collectionExists(DEFAULT_LOCK_STORE_NAME));

        assertTrue(mongoDBTestHelper.collectionExists(CUSTOM_AUDIT_REPOSITORY_NAME));
        assertTrue(mongoDBTestHelper.collectionExists(CUSTOM_LOCK_REPOSITORY_NAME));
    }

    @Test
    @DisplayName("When standalone runs the driver with transactions enabled should persist the audit logs and the user's collection updated")
    void happyPathWithTransaction() {
        //Given-When-Then
        AuditTestSupport.withTestKit(testKit)
                .GIVEN_ChangeUnits(
                        new CodeChangeUnitTestDefinition(_001_create_client_collection_happy.class, Collections.singletonList(MongoDatabase.class)),
                        new CodeChangeUnitTestDefinition(_002_insert_federico_happy_transactional.class, Arrays.asList(MongoDatabase.class, ClientSession.class)),
                        new CodeChangeUnitTestDefinition(_003_insert_jorge_happy_transactional.class, Arrays.asList(MongoDatabase.class, ClientSession.class))
                )
                .WHEN(() -> testKit.createBuilder()
                        .setRelaxTargetSystemValidation(true)
                        .addDependency(mongoClient)
                        .addDependency(database)
                        .build()
                        .run())
                .THEN_VerifyAuditSequenceStrict(
                        STARTED("create-client-collection"),
                        EXECUTED("create-client-collection"),
                        STARTED("insert-federico-document"),
                        EXECUTED("insert-federico-document"),
                        STARTED("insert-jorge-document"),
                        EXECUTED("insert-jorge-document")
                )
                .run();

        //Checking clients collection
        Set<String> clients = database.getCollection(CLIENTS_COLLECTION)
                .find()
                .map(document -> document.getString("name"))
                .into(new HashSet<>());
        assertEquals(2, clients.size());
        assertTrue(clients.contains("Federico"));
        assertTrue(clients.contains("Jorge"));
    }

    @Test
    @DisplayName("When standalone runs the driver with transactions enabled and execution fails should persist only the executed audit logs")
    void failedWithTransaction() {
        //Given-When-Then
        AuditTestSupport.withTestKit(testKit)
                .GIVEN_ChangeUnits(
                        new CodeChangeUnitTestDefinition(_001_create_client_collection_happy.class, Collections.singletonList(MongoDatabase.class)),
                        new CodeChangeUnitTestDefinition(_002_insert_federico_happy_non_transactional.class, Collections.singletonList(MongoDatabase.class)),
                        new CodeChangeUnitTestDefinition(_003_insert_jorge_failed_transactional_non_rollback.class, Arrays.asList(MongoDatabase.class, ClientSession.class))
                )
                .WHEN(() -> assertThrows(PipelineExecutionException.class, () -> {
                    testKit.createBuilder()
                            .setRelaxTargetSystemValidation(true)
                            .addDependency(mongoClient)
                            .addDependency(database)
                            .build()
                            .run();
                }))
                .THEN_VerifyAuditSequenceStrict(
                        STARTED("create-client-collection"),
                        EXECUTED("create-client-collection"),
                        STARTED("insert-federico-document"),
                        EXECUTED("insert-federico-document"),
                        STARTED("insert-jorge-document"),
                        EXECUTION_FAILED("insert-jorge-document"),
                        ROLLED_BACK("insert-jorge-document")
                )
                .run();

        //Checking clients collection
        Set<String> clients = database.getCollection(CLIENTS_COLLECTION)
                .find()
                .map(document -> document.getString("name"))
                .into(new HashSet<>());
        assertEquals(1, clients.size());
        assertTrue(clients.contains("Federico"));
    }


}