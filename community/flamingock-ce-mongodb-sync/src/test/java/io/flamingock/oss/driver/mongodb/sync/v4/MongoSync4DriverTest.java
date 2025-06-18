/*
 * Copyright 2023 Flamingock (https://oss.flamingock.io)
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

package io.flamingock.oss.driver.mongodb.sync.v4;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.ReadConcernLevel;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import io.flamingock.cloud.transaction.mongodb.sync.config.MongoDBSync4Configuration;
import io.flamingock.common.test.pipeline.CodeChangeUnitTestDefinition;
import io.flamingock.common.test.pipeline.PipelineTestHelper;
import io.flamingock.commons.utils.Trio;
import io.flamingock.internal.core.builder.FlamingockFactory;
import io.flamingock.core.audit.AuditEntry;
import io.flamingock.core.processor.util.Deserializer;
import io.flamingock.internal.core.runner.PipelineExecutionException;
import io.flamingock.oss.driver.mongodb.sync.v4.changes._1_create_client_collection_happy;
import io.flamingock.oss.driver.mongodb.sync.v4.changes._2_insert_federico_happy_transactional;
import io.flamingock.oss.driver.mongodb.sync.v4.changes._3_insert_jorge_failed_non_transactional_non_rollback;
import io.flamingock.oss.driver.mongodb.sync.v4.changes._3_insert_jorge_happy_transactional;
import io.flamingock.oss.driver.mongodb.sync.v4.changes._2_insert_federico_happy_non_transactional;
import io.flamingock.oss.driver.mongodb.sync.v4.changes._3_insert_jorge_happy_non_transactional;
import io.flamingock.oss.driver.mongodb.sync.v4.changes._3_insert_jorge_failed_transactional_non_rollback;
import io.flamingock.oss.driver.mongodb.sync.v4.changes._3_insert_jorge_failed_non_transactional_rollback;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static io.flamingock.oss.driver.common.mongodb.MongoDBDriverConfiguration.DEFAULT_LOCK_REPOSITORY_NAME;
import static io.flamingock.oss.driver.common.mongodb.MongoDBDriverConfiguration.DEFAULT_AUDIT_REPOSITORY_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class MongoSync4DriverTest {

    private static final String DB_NAME = "test";

    private static final String CLIENTS_COLLECTION = "clientCollection";

    private static final String CUSTOM_AUDIT_REPOSITORY_NAME = "testFlamingockAudit";
    private static final String CUSTOM_LOCK_REPOSITORY_NAME = "testFlamingockLock";

    private static MongoClient mongoClient;

    private static MongoDatabase mongoDatabase;

    private static MongoDBTestHelper mongoDBTestHelper;


    @Container
    public static final MongoDBContainer mongoDBContainer = new MongoDBContainer(DockerImageName.parse("mongo:6"));

    @BeforeAll
    static void beforeAll() {
        mongoClient = MongoClients.create(MongoClientSettings
                .builder()
                .applyConnectionString(new ConnectionString(mongoDBContainer.getConnectionString()))
                .build());
        mongoDatabase = mongoClient.getDatabase(DB_NAME);
        mongoDBTestHelper = new MongoDBTestHelper(mongoDatabase);
    }

    @BeforeEach
    void setupEach() {
        mongoDatabase.getCollection(DEFAULT_AUDIT_REPOSITORY_NAME).drop();
        mongoDatabase.getCollection(DEFAULT_LOCK_REPOSITORY_NAME).drop();
        mongoDatabase.getCollection(CUSTOM_AUDIT_REPOSITORY_NAME).drop();
        mongoDatabase.getCollection(CUSTOM_LOCK_REPOSITORY_NAME).drop();
    }

    @AfterEach
    void tearDownEach() {
        mongoDatabase.getCollection(CLIENTS_COLLECTION).drop();
    }

    @Test
    @DisplayName("When standalone runs the driver with DEFAULT repository names related collections should exists")
    void happyPathWithDefaultRepositoryNames() {
        //Given-When
        try (MockedStatic<Deserializer> mocked = Mockito.mockStatic(Deserializer.class)) {
            mocked.when(Deserializer::readPreviewPipelineFromFile).thenReturn(PipelineTestHelper.getPreviewPipeline(
                    new CodeChangeUnitTestDefinition(_1_create_client_collection_happy.class, Collections.singletonList(MongoDatabase.class)),
                    new CodeChangeUnitTestDefinition(_2_insert_federico_happy_transactional.class, Arrays.asList(MongoDatabase.class, ClientSession.class)),
                    new CodeChangeUnitTestDefinition(_3_insert_jorge_happy_transactional.class, Arrays.asList(MongoDatabase.class, ClientSession.class)))
            );

            FlamingockFactory.getCommunityBuilder()
                    .addDependency(mongoClient)
                    .addDependency("mongodb.databaseName", DB_NAME)
                    //.addStage(new Stage("stage-name").addCodePackage("io.flamingock.oss.driver.mongodb.sync.v4.changes.happyPathWithTransaction"))
                    .addDependency(mongoClient.getDatabase(DB_NAME))
                    .build()
                    .run();
        }

        assertTrue(mongoDBTestHelper.collectionExists(DEFAULT_AUDIT_REPOSITORY_NAME));
        assertTrue(mongoDBTestHelper.collectionExists(DEFAULT_LOCK_REPOSITORY_NAME));

        assertFalse(mongoDBTestHelper.collectionExists(CUSTOM_AUDIT_REPOSITORY_NAME));
        assertFalse(mongoDBTestHelper.collectionExists(CUSTOM_LOCK_REPOSITORY_NAME));
    }

    @Test
    @DisplayName("When standalone runs the driver with CUSTOM config properties all properties are correctly set")
    void happyPathWithCustomConfigOptions() {
        //Given-When

        MongoDBSync4Configuration config = new MongoDBSync4Configuration();

        try (MockedStatic<Deserializer> mocked = Mockito.mockStatic(Deserializer.class)) {
            mocked.when(Deserializer::readPreviewPipelineFromFile).thenReturn(PipelineTestHelper.getPreviewPipeline(
                    new CodeChangeUnitTestDefinition(_1_create_client_collection_happy.class, Collections.singletonList(MongoDatabase.class)),
                    new CodeChangeUnitTestDefinition(_2_insert_federico_happy_transactional.class, Arrays.asList(MongoDatabase.class, ClientSession.class)),
                    new CodeChangeUnitTestDefinition(_3_insert_jorge_happy_transactional.class, Arrays.asList(MongoDatabase.class, ClientSession.class)))
            );

            FlamingockFactory.getCommunityBuilder()
                    .addDependency(config)
                    .addDependency(mongoClient)
                    .setProperty("mongodb.databaseName", DB_NAME)
                    .setProperty("mongodb.indexCreation", true)
                    .setProperty("mongodb.auditRepositoryName", CUSTOM_AUDIT_REPOSITORY_NAME)
                    .setProperty("mongodb.lockRepositoryName", CUSTOM_LOCK_REPOSITORY_NAME)
                    .setProperty("mongodb.readConcern", "LOCAL")
//                    .setProperty("mongodb.writeConcern.w", 1)
//                    .setProperty("mongodb.writeConcern.journal", false)
//                    .setProperty("mongodb.writeConcern.wTimeout", Duration.ofSeconds(2))
                    .setProperty("mongodb.readPreference", MongoDBSync4Configuration.ReadPreferenceLevel.SECONDARY)
                    //.addStage(new Stage("stage-name").addCodePackage("io.flamingock.oss.driver.mongodb.sync.v4.changes.happyPathWithTransaction"))
                    .addDependency(mongoClient.getDatabase(DB_NAME))
                    .build()
                    .run();
        }

        assertTrue(config.isAutoCreate());
        //TODO: assertFalse(config.isIndexCreation());
        assertEquals(CUSTOM_AUDIT_REPOSITORY_NAME, config.getAuditRepositoryName());
        assertEquals(CUSTOM_LOCK_REPOSITORY_NAME, config.getLockRepositoryName());
        assertEquals(ReadConcernLevel.LOCAL, config.getReadConcern());
//        assertEquals(1, config.getWriteConcern().getW());
//        assertEquals(false, config.getWriteConcern().isJournal());
//        assertEquals(Duration.ofSeconds(2), config.getWriteConcern().getwTimeoutMs());
        assertEquals(MongoDBSync4Configuration.ReadPreferenceLevel.SECONDARY, config.getReadPreference());

        assertFalse(mongoDBTestHelper.collectionExists(DEFAULT_AUDIT_REPOSITORY_NAME));
        assertFalse(mongoDBTestHelper.collectionExists(DEFAULT_LOCK_REPOSITORY_NAME));

        assertTrue(mongoDBTestHelper.collectionExists(CUSTOM_AUDIT_REPOSITORY_NAME));
        assertTrue(mongoDBTestHelper.collectionExists(CUSTOM_LOCK_REPOSITORY_NAME));
    }

    @Test
    @DisplayName("When standalone runs the driver with transactions enabled should persist the audit logs and the user's collection updated")
    void happyPathWithTransaction() {

        try (MockedStatic<Deserializer> mocked = Mockito.mockStatic(Deserializer.class)) {
            mocked.when(Deserializer::readPreviewPipelineFromFile).thenReturn(PipelineTestHelper.getPreviewPipeline(
                    new CodeChangeUnitTestDefinition(_1_create_client_collection_happy.class, Collections.singletonList(MongoDatabase.class)),
                    new CodeChangeUnitTestDefinition(_2_insert_federico_happy_transactional.class, Arrays.asList(MongoDatabase.class, ClientSession.class)),
                    new CodeChangeUnitTestDefinition(_3_insert_jorge_happy_transactional.class, Arrays.asList(MongoDatabase.class, ClientSession.class)))
            );

            FlamingockFactory.getCommunityBuilder()
                    .addDependency(mongoClient)
                    .addDependency("mongodb.databaseName", DB_NAME)
                    //.addStage(new Stage("stage-name").addCodePackage("io.flamingock.oss.driver.mongodb.sync.v4.changes.happyPathWithTransaction"))
                    .addDependency(mongoClient.getDatabase(DB_NAME))
                    .build()
                    .run();
        }


        //Then
        //Checking auditLog
        List<AuditEntry> auditLog = mongoDBTestHelper.getAuditEntriesSorted(DEFAULT_AUDIT_REPOSITORY_NAME);
        assertEquals(3, auditLog.size());
        assertEquals("create-client-collection", auditLog.get(0).getTaskId());
        assertEquals(AuditEntry.Status.EXECUTED, auditLog.get(0).getState());
        assertEquals("insert-federico-document", auditLog.get(1).getTaskId());
        assertEquals(AuditEntry.Status.EXECUTED, auditLog.get(1).getState());
        assertEquals("insert-jorge-document", auditLog.get(2).getTaskId());
        assertEquals(AuditEntry.Status.EXECUTED, auditLog.get(2).getState());

        //Checking clients collection
        Set<String> clients = mongoDatabase.getCollection(CLIENTS_COLLECTION)
                .find()
                .map(document -> document.getString("name"))
                .into(new HashSet<>());
        assertEquals(2, clients.size());
        assertTrue(clients.contains("Federico"));
        assertTrue(clients.contains("Jorge"));
    }

    @Test
    @DisplayName("When standalone runs the driver with transactions disabled should persist the audit logs and the user's collection updated")
    void happyPathWithoutTransaction() {
        //Given-When
        try (MockedStatic<Deserializer> mocked = Mockito.mockStatic(Deserializer.class)) {
            mocked.when(Deserializer::readPreviewPipelineFromFile).thenReturn(PipelineTestHelper.getPreviewPipeline(
                    new CodeChangeUnitTestDefinition(_1_create_client_collection_happy.class, Collections.singletonList(MongoDatabase.class)),
                    new CodeChangeUnitTestDefinition(_2_insert_federico_happy_non_transactional.class, Collections.singletonList(MongoDatabase.class)),
                    new CodeChangeUnitTestDefinition(_3_insert_jorge_happy_non_transactional.class, Collections.singletonList(MongoDatabase.class)))
            );

            FlamingockFactory.getCommunityBuilder()
                    .addDependency(mongoClient)
                    .addDependency("mongodb.databaseName", DB_NAME)
                    //.addStage(new Stage("stage-name").addCodePackage("io.flamingock.oss.driver.mongodb.sync.v4.changes.happyPathWithoutTransaction"))
                    .addDependency(mongoClient.getDatabase(DB_NAME))
                    .disableTransaction()
                    .build()
                    .run();
        }



        //Then
        //Checking auditLog
        List<AuditEntry> auditLog = mongoDBTestHelper.getAuditEntriesSorted(DEFAULT_AUDIT_REPOSITORY_NAME);
        assertEquals(3, auditLog.size());
        assertEquals("create-client-collection", auditLog.get(0).getTaskId());
        assertEquals(AuditEntry.Status.EXECUTED, auditLog.get(0).getState());
        assertEquals("insert-federico-document", auditLog.get(1).getTaskId());
        assertEquals(AuditEntry.Status.EXECUTED, auditLog.get(1).getState());
        assertEquals("insert-jorge-document", auditLog.get(2).getTaskId());
        assertEquals(AuditEntry.Status.EXECUTED, auditLog.get(2).getState());

        //Checking clients collection
        Set<String> clients = mongoDatabase.getCollection(CLIENTS_COLLECTION)
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
        //Given-When
        try (MockedStatic<Deserializer> mocked = Mockito.mockStatic(Deserializer.class)) {
            mocked.when(Deserializer::readPreviewPipelineFromFile).thenReturn(PipelineTestHelper.getPreviewPipeline(
                    new CodeChangeUnitTestDefinition(_1_create_client_collection_happy.class, Collections.singletonList(MongoDatabase.class)),
                    new CodeChangeUnitTestDefinition(_2_insert_federico_happy_non_transactional.class, Collections.singletonList(MongoDatabase.class)),
                    new CodeChangeUnitTestDefinition(_3_insert_jorge_failed_transactional_non_rollback.class, Arrays.asList(MongoDatabase.class, ClientSession.class)))
            );

            assertThrows(PipelineExecutionException.class, () -> {
                FlamingockFactory.getCommunityBuilder()
                        .addDependency(mongoClient)
                        .addDependency("mongodb.databaseName", DB_NAME)
                        //.addStage(new Stage("stage-name").addCodePackage("io.flamingock.oss.driver.mongodb.sync.v4.changes.failedWithTransaction"))
                        .addDependency(mongoClient.getDatabase(DB_NAME))
                        .build()
                        .run();
            });
        }

        //Then
        //Checking auditLog
        List<AuditEntry> auditLog = mongoDBTestHelper.getAuditEntriesSorted(DEFAULT_AUDIT_REPOSITORY_NAME);
        assertEquals(3, auditLog.size());
        assertEquals("create-client-collection", auditLog.get(0).getTaskId());
        assertEquals(AuditEntry.Status.EXECUTED, auditLog.get(0).getState());
        assertEquals("insert-federico-document", auditLog.get(1).getTaskId());
        assertEquals(AuditEntry.Status.EXECUTED, auditLog.get(1).getState());

        //Checking clients collection
        Set<String> clients = mongoDatabase.getCollection(CLIENTS_COLLECTION)
                .find()
                .map(document -> document.getString("name"))
                .into(new HashSet<>());
        assertEquals(1, clients.size());
        assertTrue(clients.contains("Federico"));
    }

    @Test
    @DisplayName("When standalone runs the driver with transactions disabled and execution fails (with rollback method) should persist all the audit logs up to the failed one (ROLLED_BACK)")
    void failedWithoutTransactionWithRollback() {
        //Given-When

        try (MockedStatic<Deserializer> mocked = Mockito.mockStatic(Deserializer.class)) {
            mocked.when(Deserializer::readPreviewPipelineFromFile).thenReturn(PipelineTestHelper.getPreviewPipeline(
                    new CodeChangeUnitTestDefinition(_1_create_client_collection_happy.class, Collections.singletonList(MongoDatabase.class)),
                    new CodeChangeUnitTestDefinition(_2_insert_federico_happy_non_transactional.class, Collections.singletonList(MongoDatabase.class)),
                    new CodeChangeUnitTestDefinition(_3_insert_jorge_failed_non_transactional_rollback.class, Collections.singletonList(MongoDatabase.class), Collections.singletonList(MongoDatabase.class)))
            );

            assertThrows(PipelineExecutionException.class, () -> {
                FlamingockFactory.getCommunityBuilder()
                        .addDependency(mongoClient)
                        .addDependency("mongodb.databaseName", DB_NAME)
                        //.addStage(new Stage("stage-name").addCodePackage("io.flamingock.oss.driver.mongodb.sync.v4.changes.failedWithoutTransactionWithRollback"))
                        .addDependency(mongoClient.getDatabase(DB_NAME))
                        .disableTransaction()
                        .build()
                        .run();
            });
        }



        //Then
        //Checking auditLog
        List<AuditEntry> auditLog = mongoDBTestHelper.getAuditEntriesSorted(DEFAULT_AUDIT_REPOSITORY_NAME);
        assertEquals(3, auditLog.size());
        assertEquals("create-client-collection", auditLog.get(0).getTaskId());
        assertEquals(AuditEntry.Status.EXECUTED, auditLog.get(0).getState());
        assertEquals("insert-federico-document", auditLog.get(1).getTaskId());
        assertEquals(AuditEntry.Status.EXECUTED, auditLog.get(1).getState());
        assertEquals("insert-jorge-document", auditLog.get(2).getTaskId());
        assertEquals(AuditEntry.Status.ROLLED_BACK, auditLog.get(2).getState());

        //Checking clients collection
        Set<String> clients = mongoDatabase.getCollection(CLIENTS_COLLECTION)
                .find()
                .map(document -> document.getString("name"))
                .into(new HashSet<>());
        assertEquals(1, clients.size());
        assertTrue(clients.contains("Federico"));
    }

    @Test
    @DisplayName("When standalone runs the driver with transactions disabled and execution fails (without rollback method) should persist all the audit logs up to the failed one (FAILED)")
    void failedWithoutTransactionWithoutRollback() {
        //Given-When
        try (MockedStatic<Deserializer> mocked = Mockito.mockStatic(Deserializer.class)) {
            mocked.when(Deserializer::readPreviewPipelineFromFile).thenReturn(PipelineTestHelper.getPreviewPipeline(
                    new CodeChangeUnitTestDefinition(_1_create_client_collection_happy.class, Collections.singletonList(MongoDatabase.class)),
                    new CodeChangeUnitTestDefinition(_2_insert_federico_happy_non_transactional.class, Collections.singletonList(MongoDatabase.class)),
                    new CodeChangeUnitTestDefinition(_3_insert_jorge_failed_non_transactional_non_rollback.class, Collections.singletonList(MongoDatabase.class), Collections.singletonList(MongoDatabase.class)))
            );

            assertThrows(PipelineExecutionException.class, () -> {
                FlamingockFactory.getCommunityBuilder()
                        .addDependency(mongoClient)
                        .addDependency("mongodb.databaseName", DB_NAME)
                        //.addStage(new Stage("stage-name").addCodePackage("io.flamingock.oss.driver.mongodb.sync.v4.changes.failedWithoutTransactionWithoutRollback"))
                        .addDependency(mongoClient.getDatabase(DB_NAME))
                        .disableTransaction()
                        .build()
                        .run();
            });
        }


        //Then
        //Checking auditLog
        List<AuditEntry> auditLog = mongoDBTestHelper.getAuditEntriesSorted(DEFAULT_AUDIT_REPOSITORY_NAME);
        assertEquals(3, auditLog.size());
        assertEquals("create-client-collection", auditLog.get(0).getTaskId());
        assertEquals(AuditEntry.Status.EXECUTED, auditLog.get(0).getState());
        assertEquals("insert-federico-document", auditLog.get(1).getTaskId());
        assertEquals(AuditEntry.Status.EXECUTED, auditLog.get(1).getState());
        assertEquals("insert-jorge-document", auditLog.get(2).getTaskId());
        assertEquals(AuditEntry.Status.EXECUTION_FAILED, auditLog.get(2).getState());

        //Checking clients collection
        Set<String> clients = mongoDatabase.getCollection(CLIENTS_COLLECTION)
                .find()
                .map(document -> document.getString("name"))
                .into(new HashSet<>());
        assertEquals(2, clients.size());
        assertTrue(clients.contains("Federico"));
        assertTrue(clients.contains("Jorge"));
    }


}