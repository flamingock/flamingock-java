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

package io.flamingock.community.mongodb.springdata;


import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import io.flamingock.internal.util.Trio;
import io.flamingock.internal.core.builder.core.CoreConfiguration;
import io.flamingock.internal.core.builder.FlamingockFactory;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.core.processor.util.Deserializer;
import io.flamingock.internal.core.runner.PipelineExecutionException;
import io.flamingock.community.mongodb.springdata.changes._001_create_client_collection_happy;
import io.flamingock.community.mongodb.springdata.changes._002_insert_federico_happy_non_transactional;
import io.flamingock.community.mongodb.springdata.changes._002_insert_federico_happy_transactional;
import io.flamingock.community.mongodb.springdata.changes._003_insert_jorge_failed_non_transactional_non_rollback;
import io.flamingock.community.mongodb.springdata.changes._003_insert_jorge_failed_non_transactional_rollback;
import io.flamingock.community.mongodb.springdata.changes._003_insert_jorge_failed_transactional_non_rollback;
import io.flamingock.community.mongodb.springdata.changes._003_insert_jorge_happy_non_transactional;
import io.flamingock.community.mongodb.springdata.changes._003_insert_jorge_happy_transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static io.flamingock.internal.core.community.Constants.DEFAULT_LOCK_STORE_NAME;
import static io.flamingock.internal.core.community.Constants.DEFAULT_AUDIT_STORE_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class SpringDataMongoV3DriverTest {

    @Container
    public static final MongoDBContainer mongoDBContainer = new MongoDBContainer(DockerImageName.parse("mongo:4.0.10"));
    private static final String DB_NAME = "test";
    private static final String CLIENTS_COLLECTION = "clientCollection";
    private static final String CUSTOM_AUDIT_REPOSITORY_NAME = "testFlamingockAudit";
    private static final String CUSTOM_LOCK_REPOSITORY_NAME = "testFlamingockLock";
    private static MongoTemplate mongoTemplate;
    private static MongoDBTestHelper mongoDBTestHelper;

    @BeforeAll
    static void beforeAll() {
        MongoClient mongoClient = MongoClients.create(MongoClientSettings
                .builder()
                .applyConnectionString(new ConnectionString(mongoDBContainer.getConnectionString()))
                .build());
        mongoTemplate = new MongoTemplate(mongoClient, DB_NAME);
        mongoDBTestHelper = new MongoDBTestHelper(mongoTemplate.getDb());
    }

    @BeforeEach
    void setupEach() {
        mongoTemplate.getCollection(DEFAULT_AUDIT_STORE_NAME).drop();
        mongoTemplate.getCollection(DEFAULT_LOCK_STORE_NAME).drop();
        mongoTemplate.getCollection(CUSTOM_AUDIT_REPOSITORY_NAME).drop();
        mongoTemplate.getCollection(CUSTOM_LOCK_REPOSITORY_NAME).drop();
    }

    @AfterEach
    void tearDownEach() {
        mongoTemplate.getCollection(CLIENTS_COLLECTION).drop();
    }

    @Test
    @DisplayName("When standalone runs the driver with DEFAULT repository names related collections should exists")
    void happyPathWithDefaultRepositoryNames() {

        //Given-When
        try (MockedStatic<Deserializer> mocked = Mockito.mockStatic(Deserializer.class)) {
            mocked.when(Deserializer::readPreviewPipelineFromFile).thenReturn(MongoDBTestHelper.getPreviewPipeline(
                    new Trio<>(_001_create_client_collection_happy.class, Collections.singletonList(MongoTemplate.class)),
                    new Trio<>(_002_insert_federico_happy_transactional.class, Collections.singletonList(MongoTemplate.class)),
                    new Trio<>(_003_insert_jorge_happy_non_transactional.class, Collections.singletonList(MongoTemplate.class)))
            );

            FlamingockFactory.getCommunityBuilder()
                    .withImporter(CoreConfiguration.ImporterConfiguration.withSource("mongockChangeLog"))
                    //.addStage(new Stage("stage-name").addCodePackage("io.flamingock.oss.driver.mongodb.springdata.v3.changes.happyPathWithTransaction"))
                    .addDependency(mongoTemplate)
                    .build()
                    .run();
        }


        assertTrue(mongoDBTestHelper.collectionExists(DEFAULT_AUDIT_STORE_NAME));
        assertTrue(mongoDBTestHelper.collectionExists(DEFAULT_LOCK_STORE_NAME));

        assertFalse(mongoDBTestHelper.collectionExists(CUSTOM_AUDIT_REPOSITORY_NAME));
        assertFalse(mongoDBTestHelper.collectionExists(CUSTOM_LOCK_REPOSITORY_NAME));
    }

    @Test
    @DisplayName("When standalone runs the driver with CUSTOM repository names related collections should exists")
    void happyPathWithCustomRepositoryNames() {
        //Given-When

        try (MockedStatic<Deserializer> mocked = Mockito.mockStatic(Deserializer.class)) {
            mocked.when(Deserializer::readPreviewPipelineFromFile).thenReturn(MongoDBTestHelper.getPreviewPipeline(
                    new Trio<>(_001_create_client_collection_happy.class, Collections.singletonList(MongoTemplate.class)),
                    new Trio<>(_002_insert_federico_happy_transactional.class, Collections.singletonList(MongoTemplate.class)),
                    new Trio<>(_003_insert_jorge_happy_transactional.class, Collections.singletonList(MongoTemplate.class)))
            );
            FlamingockFactory.getCommunityBuilder()
                    .setProperty("mongodb.auditRepositoryName", CUSTOM_AUDIT_REPOSITORY_NAME)
                    .setProperty("mongodb.lockRepositoryName", CUSTOM_LOCK_REPOSITORY_NAME)
                    //.addStage(new Stage("stage-name").addCodePackage("io.flamingock.oss.driver.mongodb.springdata.v3.changes.happyPathWithTransaction"))
                    .addDependency(mongoTemplate)
                    .build()
                    .run();
        }

        assertFalse(mongoDBTestHelper.collectionExists(DEFAULT_AUDIT_STORE_NAME));
        assertFalse(mongoDBTestHelper.collectionExists(DEFAULT_LOCK_STORE_NAME));

        assertTrue(mongoDBTestHelper.collectionExists(CUSTOM_AUDIT_REPOSITORY_NAME));
        assertTrue(mongoDBTestHelper.collectionExists(CUSTOM_LOCK_REPOSITORY_NAME));
    }

    @Test
    @DisplayName("When standalone runs the driver with transactions enabled should persist the audit logs and the user's collection updated")
    void happyPathWithTransaction() {
        //Given-When
        try (MockedStatic<Deserializer> mocked = Mockito.mockStatic(Deserializer.class)) {
            mocked.when(Deserializer::readPreviewPipelineFromFile).thenReturn(MongoDBTestHelper.getPreviewPipeline(
                    new Trio<>(_001_create_client_collection_happy.class, Collections.singletonList(MongoTemplate.class)),
                    new Trio<>(_002_insert_federico_happy_transactional.class, Collections.singletonList(MongoTemplate.class)),
                    new Trio<>(_003_insert_jorge_happy_transactional.class, Collections.singletonList(MongoTemplate.class)))
            );

            FlamingockFactory.getCommunityBuilder()
                    //.addStage(new Stage("stage-name").addCodePackage("io.flamingock.oss.driver.mongodb.springdata.v3.changes.happyPathWithTransaction"))
                    .addDependency(mongoTemplate)
                    .build()
                    .run();
        }

        //Then
        //Checking auditLog
        List<AuditEntry> auditLog = mongoDBTestHelper.getAuditEntriesSorted(DEFAULT_AUDIT_STORE_NAME);
        assertEquals(3, auditLog.size());
        assertEquals("create-client-collection", auditLog.get(0).getTaskId());
        assertEquals(AuditEntry.Status.EXECUTED, auditLog.get(0).getState());
        assertEquals("insert-federico-document", auditLog.get(1).getTaskId());
        assertEquals(AuditEntry.Status.EXECUTED, auditLog.get(1).getState());
        assertEquals("insert-jorge-document", auditLog.get(2).getTaskId());
        assertEquals(AuditEntry.Status.EXECUTED, auditLog.get(2).getState());

        //Checking clients collection
        Set<String> clients = mongoTemplate.getCollection(CLIENTS_COLLECTION)
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
            mocked.when(Deserializer::readPreviewPipelineFromFile).thenReturn(MongoDBTestHelper.getPreviewPipeline(
                    new Trio<>(_001_create_client_collection_happy.class, Collections.singletonList(MongoTemplate.class)),
                    new Trio<>(_002_insert_federico_happy_non_transactional.class, Collections.singletonList(MongoTemplate.class)),
                    new Trio<>(_003_insert_jorge_happy_non_transactional.class, Collections.singletonList(MongoTemplate.class)))
            );
            FlamingockFactory.getCommunityBuilder()
                    //.addStage(new Stage("stage-name").addCodePackage("io.flamingock.oss.driver.mongodb.springdata.v3.changes.happyPathWithoutTransaction"))
                    .addDependency(mongoTemplate)
                    .disableTransaction()
                    .build()
                    .run();
        }


        //Then
        //Checking auditLog
        List<AuditEntry> auditLog = mongoDBTestHelper.getAuditEntriesSorted(DEFAULT_AUDIT_STORE_NAME);
        assertEquals(3, auditLog.size());
        assertEquals("create-client-collection", auditLog.get(0).getTaskId());
        assertEquals(AuditEntry.Status.EXECUTED, auditLog.get(0).getState());
        assertEquals("insert-federico-document", auditLog.get(1).getTaskId());
        assertEquals(AuditEntry.Status.EXECUTED, auditLog.get(1).getState());
        assertEquals("insert-jorge-document", auditLog.get(2).getTaskId());
        assertEquals(AuditEntry.Status.EXECUTED, auditLog.get(2).getState());

        //Checking clients collection
        Set<String> clients = mongoTemplate.getCollection(CLIENTS_COLLECTION)
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
            mocked.when(Deserializer::readPreviewPipelineFromFile).thenReturn(MongoDBTestHelper.getPreviewPipeline(
                    new Trio<>(_001_create_client_collection_happy.class, Collections.singletonList(MongoTemplate.class)),
                    new Trio<>(_002_insert_federico_happy_non_transactional.class, Collections.singletonList(MongoTemplate.class)),
                    new Trio<>(_003_insert_jorge_failed_transactional_non_rollback.class, Collections.singletonList(MongoTemplate.class)))
            );

            assertThrows(PipelineExecutionException.class, () -> {
                FlamingockFactory.getCommunityBuilder()
                        //.addStage(new Stage("stage-name").addCodePackage("io.flamingock.oss.driver.mongodb.springdata.v3.changes.failedWithTransaction"))
                        .addDependency(mongoTemplate)
                        .build()
                        .run();
            });
        }

        //Then
        //Checking auditLog
        List<AuditEntry> auditLog = mongoDBTestHelper.getAuditEntriesSorted(DEFAULT_AUDIT_STORE_NAME);
        assertEquals(3, auditLog.size());
        assertEquals("create-client-collection", auditLog.get(0).getTaskId());
        assertEquals(AuditEntry.Status.EXECUTED, auditLog.get(0).getState());
        assertEquals("insert-federico-document", auditLog.get(1).getTaskId());
        assertEquals(AuditEntry.Status.EXECUTED, auditLog.get(1).getState());

        //Checking clients collection
        Set<String> clients = mongoTemplate.getCollection(CLIENTS_COLLECTION)
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
            mocked.when(Deserializer::readPreviewPipelineFromFile).thenReturn(MongoDBTestHelper.getPreviewPipeline(
                    new Trio<>(_001_create_client_collection_happy.class, Collections.singletonList(MongoTemplate.class)),
                    new Trio<>(_002_insert_federico_happy_non_transactional.class, Collections.singletonList(MongoTemplate.class)),
                    new Trio<>(_003_insert_jorge_failed_non_transactional_rollback.class, Collections.singletonList(MongoTemplate.class), Collections.singletonList(MongoTemplate.class)))
            );

            assertThrows(PipelineExecutionException.class, () -> {
                FlamingockFactory.getCommunityBuilder()
                        //.addStage(new Stage("stage-name").addCodePackage("io.flamingock.oss.driver.mongodb.springdata.v3.changes.failedWithoutTransactionWithRollback"))
                        .addDependency(mongoTemplate)
                        .disableTransaction()
                        .build()
                        .run();
            });
        }


        //Then
        //Checking auditLog
        List<AuditEntry> auditLog = mongoDBTestHelper.getAuditEntriesSorted(DEFAULT_AUDIT_STORE_NAME);
        assertEquals(3, auditLog.size());
        assertEquals("create-client-collection", auditLog.get(0).getTaskId());
        assertEquals(AuditEntry.Status.EXECUTED, auditLog.get(0).getState());
        assertEquals("insert-federico-document", auditLog.get(1).getTaskId());
        assertEquals(AuditEntry.Status.EXECUTED, auditLog.get(1).getState());
        assertEquals("insert-jorge-document", auditLog.get(2).getTaskId());
        assertEquals(AuditEntry.Status.ROLLED_BACK, auditLog.get(2).getState());

        //Checking clients collection
        Set<String> clients = mongoTemplate.getCollection(CLIENTS_COLLECTION)
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
            mocked.when(Deserializer::readPreviewPipelineFromFile).thenReturn(MongoDBTestHelper.getPreviewPipeline(
                    new Trio<>(_001_create_client_collection_happy.class, Collections.singletonList(MongoTemplate.class)),
                    new Trio<>(_002_insert_federico_happy_non_transactional.class, Collections.singletonList(MongoTemplate.class)),
                    new Trio<>(_003_insert_jorge_failed_non_transactional_non_rollback.class, Collections.singletonList(MongoTemplate.class), Collections.singletonList(MongoTemplate.class)))
            );

            assertThrows(PipelineExecutionException.class, () -> {
                FlamingockFactory.getCommunityBuilder()
                        //.addStage(new Stage("stage-name").addCodePackage("io.flamingock.oss.driver.mongodb.springdata.v3.changes.failedWithoutTransactionWithoutRollback"))
                        .addDependency(mongoTemplate)
                        .disableTransaction()
                        .build()
                        .run();
            });
        }


        //Then
        //Checking auditLog
        List<AuditEntry> auditLog = mongoDBTestHelper.getAuditEntriesSorted(DEFAULT_AUDIT_STORE_NAME);
        assertEquals(3, auditLog.size());
        assertEquals("create-client-collection", auditLog.get(0).getTaskId());
        assertEquals(AuditEntry.Status.EXECUTED, auditLog.get(0).getState());
        assertEquals("insert-federico-document", auditLog.get(1).getTaskId());
        assertEquals(AuditEntry.Status.EXECUTED, auditLog.get(1).getState());
        assertEquals("insert-jorge-document", auditLog.get(2).getTaskId());
        assertEquals(AuditEntry.Status.EXECUTION_FAILED, auditLog.get(2).getState());

        //Checking clients collection
        Set<String> clients = mongoTemplate.getCollection(CLIENTS_COLLECTION)
                .find()
                .map(document -> document.getString("name"))
                .into(new HashSet<>());
        assertEquals(2, clients.size());
        assertTrue(clients.contains("Federico"));
        assertTrue(clients.contains("Jorge"));
    }
}