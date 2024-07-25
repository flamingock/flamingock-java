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

package io.flamingock.oss.driver.mongodb.v3;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;

import io.flamingock.core.engine.audit.writer.AuditEntry;
import io.flamingock.core.configurator.standalone.FlamingockStandalone;
import io.flamingock.core.engine.audit.writer.AuditEntryStatus;
import io.flamingock.core.pipeline.Stage;
import io.flamingock.core.pipeline.execution.StageExecutionException;
import io.flamingock.oss.driver.mongodb.v3.driver.Mongo3Driver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static io.flamingock.oss.driver.common.mongodb.MongoDBDriverConfiguration.LEGACY_DEFAULT_LOCK_REPOSITORY_NAME;
import static io.flamingock.oss.driver.common.mongodb.MongoDBDriverConfiguration.LEGACY_DEFAULT_MIGRATION_REPOSITORY_NAME;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class Mongo3DriverTest {

    private static final String DB_NAME = "test";

    private static final String CLIENTS_COLLECTION = "clientCollection";

    private static final String CUSTOM_MIGRATION_REPOSITORY_NAME = "testFlamingockAudit";
    private static final String CUSTOM_LOCK_REPOSITORY_NAME = "testFlamingockLock";

    private static MongoClient mongoClient;

    private static MongoDatabase mongoDatabase;

    private static MongoDBTestHelper mongoDBTestHelper;


    @Container
    public static final MongoDBContainer mongoDBContainer = new MongoDBContainer(DockerImageName.parse("mongo:4.0.10"));

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
        mongoDatabase.getCollection(LEGACY_DEFAULT_MIGRATION_REPOSITORY_NAME).drop();
        mongoDatabase.getCollection(LEGACY_DEFAULT_LOCK_REPOSITORY_NAME).drop();
        mongoDatabase.getCollection(CUSTOM_MIGRATION_REPOSITORY_NAME).drop();
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
        FlamingockStandalone.local()
                .setDriver(new Mongo3Driver(mongoClient, DB_NAME))
                .addStage(new Stage("stage-name").addCodePackage("io.flamingock.oss.driver.mongodb.v3.changes.happyPathWithTransaction"))
                .addDependency(mongoClient.getDatabase(DB_NAME))
                .setTrackIgnored(true)
                .setTransactionEnabled(true)
                .build()
                .run();

        assertTrue(mongoDBTestHelper.collectionExists(LEGACY_DEFAULT_MIGRATION_REPOSITORY_NAME));
        assertTrue(mongoDBTestHelper.collectionExists(LEGACY_DEFAULT_LOCK_REPOSITORY_NAME));

        assertFalse(mongoDBTestHelper.collectionExists(CUSTOM_MIGRATION_REPOSITORY_NAME));
        assertFalse(mongoDBTestHelper.collectionExists(CUSTOM_LOCK_REPOSITORY_NAME));
    }

    @Test
    @DisplayName("When standalone runs the driver with CUSTOM repository names related collections should exists")
    void happyPathWithCustomRepositoryNames() {
        //Given-When
        MongoDB3Configuration driverConfiguration = MongoDB3Configuration.getDefault();
        driverConfiguration.setMigrationRepositoryName(CUSTOM_MIGRATION_REPOSITORY_NAME);
        driverConfiguration.setLockRepositoryName(CUSTOM_LOCK_REPOSITORY_NAME);

        FlamingockStandalone.local()
                .setDriver(new Mongo3Driver(mongoClient, DB_NAME).setDriverConfiguration(driverConfiguration))
                .addStage(new Stage("stage-name").addCodePackage("io.flamingock.oss.driver.mongodb.v3.changes.happyPathWithTransaction"))
                .addDependency(mongoClient.getDatabase(DB_NAME))
                .setTrackIgnored(true)
                .setTransactionEnabled(true)
                .build()
                .run();

        assertFalse(mongoDBTestHelper.collectionExists(LEGACY_DEFAULT_MIGRATION_REPOSITORY_NAME));
        assertFalse(mongoDBTestHelper.collectionExists(LEGACY_DEFAULT_LOCK_REPOSITORY_NAME));

        assertTrue(mongoDBTestHelper.collectionExists(CUSTOM_MIGRATION_REPOSITORY_NAME));
        assertTrue(mongoDBTestHelper.collectionExists(CUSTOM_LOCK_REPOSITORY_NAME));
    }

    @Test
    @DisplayName("When standalone runs the driver with transactions enabled should persist the audit logs and the user's collection updated")
    void happyPathWithTransaction() {
        //Given-When
        FlamingockStandalone.local()
                .setDriver(new Mongo3Driver(mongoClient, DB_NAME))
                .addStage(new Stage("stage-name").addCodePackage("io.flamingock.oss.driver.mongodb.v3.changes.happyPathWithTransaction"))
                .addDependency(mongoClient.getDatabase(DB_NAME))
                .setTrackIgnored(true)
                .setTransactionEnabled(true)
                .build()
                .run();

        //Then
        //Checking auditLog
        List<AuditEntry> auditLog = mongoDBTestHelper.getAuditEntriesSorted(LEGACY_DEFAULT_MIGRATION_REPOSITORY_NAME);
        assertEquals(3, auditLog.size());
        assertEquals("create-collection", auditLog.get(0).getTaskId());
        assertEquals(AuditEntryStatus.EXECUTED, auditLog.get(0).getState());
        assertEquals("insert-document", auditLog.get(1).getTaskId());
        assertEquals(AuditEntryStatus.EXECUTED, auditLog.get(1).getState());
        assertEquals("insert-another-document", auditLog.get(2).getTaskId());
        assertEquals(AuditEntryStatus.EXECUTED, auditLog.get(2).getState());

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
        FlamingockStandalone.local()
                .setDriver(new Mongo3Driver(mongoClient, DB_NAME))
                .addStage(new Stage("stage-name").addCodePackage("io.flamingock.oss.driver.mongodb.v3.changes.happyPathWithoutTransaction"))
                .addDependency(mongoClient.getDatabase(DB_NAME))
                .setTrackIgnored(true)
                .setTransactionEnabled(false)
                .build()
                .run();

        //Then
        //Checking auditLog
        List<AuditEntry> auditLog = mongoDBTestHelper.getAuditEntriesSorted(LEGACY_DEFAULT_MIGRATION_REPOSITORY_NAME);
        assertEquals(3, auditLog.size());
        assertEquals("create-collection", auditLog.get(0).getTaskId());
        assertEquals(AuditEntryStatus.EXECUTED, auditLog.get(0).getState());
        assertEquals("insert-document", auditLog.get(1).getTaskId());
        assertEquals(AuditEntryStatus.EXECUTED, auditLog.get(1).getState());
        assertEquals("insert-another-document", auditLog.get(2).getTaskId());
        assertEquals(AuditEntryStatus.EXECUTED, auditLog.get(2).getState());

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
        assertThrows(StageExecutionException.class, () -> {
            FlamingockStandalone.local()
                    .setDriver(new Mongo3Driver(mongoClient, DB_NAME))
                    .addStage(new Stage("stage-name").addCodePackage("io.flamingock.oss.driver.mongodb.v3.changes.failedWithTransaction"))
                    .addDependency(mongoClient.getDatabase(DB_NAME))
                    .setTrackIgnored(true)
                    .setTransactionEnabled(true)
                    .build()
                    .run();
        });

        //Then
        //Checking auditLog
        List<AuditEntry> auditLog = mongoDBTestHelper.getAuditEntriesSorted(LEGACY_DEFAULT_MIGRATION_REPOSITORY_NAME);
        assertEquals(3, auditLog.size());
        assertEquals("create-collection", auditLog.get(0).getTaskId());
        assertEquals(AuditEntryStatus.EXECUTED, auditLog.get(0).getState());
        assertEquals("insert-document", auditLog.get(1).getTaskId());
        assertEquals(AuditEntryStatus.EXECUTED, auditLog.get(1).getState());

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
        assertThrows(StageExecutionException.class, () -> {
            FlamingockStandalone.local()
                    .setDriver(new Mongo3Driver(mongoClient, DB_NAME))
                    .addStage(new Stage("stage-name").addCodePackage("io.flamingock.oss.driver.mongodb.v3.changes.failedWithoutTransactionWithRollback"))
                    .addDependency(mongoClient.getDatabase(DB_NAME))
                    .setTrackIgnored(true)
                    .setTransactionEnabled(false)
                    .build()
                    .run();
        });

        //Then
        //Checking auditLog
        List<AuditEntry> auditLog = mongoDBTestHelper.getAuditEntriesSorted(LEGACY_DEFAULT_MIGRATION_REPOSITORY_NAME);
        assertEquals(3, auditLog.size());
        assertEquals("create-collection", auditLog.get(0).getTaskId());
        assertEquals(AuditEntryStatus.EXECUTED, auditLog.get(0).getState());
        assertEquals("insert-document", auditLog.get(1).getTaskId());
        assertEquals(AuditEntryStatus.EXECUTED, auditLog.get(1).getState());
        assertEquals("execution-with-exception", auditLog.get(2).getTaskId());
        assertEquals(AuditEntryStatus.ROLLED_BACK, auditLog.get(2).getState());

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
        assertThrows(StageExecutionException.class, () -> {
            FlamingockStandalone.local()
                    .setDriver(new Mongo3Driver(mongoClient, DB_NAME))
                    .addStage(new Stage("stage-name").addCodePackage("io.flamingock.oss.driver.mongodb.v3.changes.failedWithoutTransactionWithoutRollback"))
                    .addDependency(mongoClient.getDatabase(DB_NAME))
                    .setTrackIgnored(true)
                    .setTransactionEnabled(false)
                    .build()
                    .run();
        });

        //Then
        //Checking auditLog
        List<AuditEntry> auditLog = mongoDBTestHelper.getAuditEntriesSorted(LEGACY_DEFAULT_MIGRATION_REPOSITORY_NAME);
        assertEquals(3, auditLog.size());
        assertEquals("create-collection", auditLog.get(0).getTaskId());
        assertEquals(AuditEntryStatus.EXECUTED, auditLog.get(0).getState());
        assertEquals("insert-document", auditLog.get(1).getTaskId());
        assertEquals(AuditEntryStatus.EXECUTED, auditLog.get(1).getState());
        assertEquals("execution-with-exception", auditLog.get(2).getTaskId());
        assertEquals(AuditEntryStatus.EXECUTION_FAILED, auditLog.get(2).getState());

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