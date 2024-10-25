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
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import io.flamingock.core.configurator.standalone.FlamingockStandalone;
import io.flamingock.core.engine.audit.writer.AuditEntry;
import io.flamingock.core.pipeline.Stage;
import io.flamingock.oss.driver.mongodb.sync.v4.driver.MongoSync4Driver;
import io.flamingock.oss.driver.mongodb.sync.v4.mongock.ClientInitializerChangeUnit;
import io.mongock.runner.standalone.MongockStandalone;
import org.bson.Document;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.ArrayList;
import java.util.List;

import static io.flamingock.core.configurator.core.CoreConfiguration.MongockImporterConfiguration;
import static io.flamingock.oss.driver.common.mongodb.MongoDBDriverConfiguration.DEFAULT_LOCK_REPOSITORY_NAME;
import static io.flamingock.oss.driver.common.mongodb.MongoDBDriverConfiguration.DEFAULT_MIGRATION_REPOSITORY_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;


@Testcontainers
class MongoSync4ImporterTest {

    private static final String DB_NAME = "test";

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
        mongoDatabase.getCollection(DEFAULT_MIGRATION_REPOSITORY_NAME).drop();
        mongoDatabase.getCollection(DEFAULT_LOCK_REPOSITORY_NAME).drop();
    }

    @Test
    @DisplayName("When standalone runs the driver with mongock importer should run migration")
    void shouldRunMongockImporter() {
        //Given
        io.mongock.driver.mongodb.sync.v4.driver.MongoSync4Driver mongoSync4Driver = io.mongock.driver.mongodb.sync.v4.driver.MongoSync4Driver
                .withDefaultLock(mongoClient, DB_NAME);
        MongockStandalone.builder()
                .setDriver(mongoSync4Driver)
                .addMigrationClass(ClientInitializerChangeUnit.class)
                .setTrackIgnored(true)
                .setTransactional(false)
                .buildRunner()
                .execute();

        ArrayList<Document> mongockDbState = mongoClient.getDatabase(DB_NAME).getCollection(mongoSync4Driver.getMigrationRepositoryName())
                .find()
                .into(new ArrayList<>());

        Assertions.assertEquals(4, mongockDbState.size());
        assertEquals("system-change-00001_before", mongockDbState.get(0).get("changeId"));
        assertEquals("system-change-00001", mongockDbState.get(1).get("changeId"));
        assertEquals("client-initializer_before", mongockDbState.get(2).get("changeId"));
        assertEquals("client-initializer", mongockDbState.get(3).get("changeId"));

        //When
        FlamingockStandalone.local()
                .setMongockImporterConfiguration(MongockImporterConfiguration.withSource(mongoSync4Driver.getMigrationRepositoryName()))
                .setDriver(new MongoSync4Driver(mongoClient, DB_NAME))
                .addStage(new Stage("stage-name").addCodePackage("io.flamingock.oss.driver.mongodb.sync.v4.changes.happyPathWithTransaction"))
                .addDependency(mongoClient.getDatabase(DB_NAME))
                .setTrackIgnored(true)
                .setTransactionEnabled(true)
                .build()
                .run();


        List<AuditEntry> auditLog = mongoDBTestHelper.getAuditEntriesSorted(DEFAULT_MIGRATION_REPOSITORY_NAME);
        assertEquals(8, auditLog.size());
        checkAuditEntry(
                auditLog.get(0),
                mongockDbState.get(0).getString("executionId"),
                null,
                mongockDbState.get(0).getString("changeId"),
                AuditEntry.Status.EXECUTED,
                mongockDbState.get(0).getString("changeLogClass"),
                mongockDbState.get(0).getString("changeSetMethod"),
                AuditEntry.ExecutionType.BEFORE_EXECUTION,
                true);
        checkAuditEntry(
                auditLog.get(1),
                mongockDbState.get(1).getString("executionId"),
                null,
                mongockDbState.get(1).getString("changeId"),
                AuditEntry.Status.EXECUTED,
                mongockDbState.get(1).getString("changeLogClass"),
                mongockDbState.get(1).getString("changeSetMethod"),
                AuditEntry.ExecutionType.EXECUTION,
                true);

        checkAuditEntry(
                auditLog.get(2),
                mongockDbState.get(2).getString("executionId"),
                null,
                mongockDbState.get(2).getString("changeId"),
                AuditEntry.Status.EXECUTED,
                mongockDbState.get(2).getString("changeLogClass"),
                mongockDbState.get(2).getString("changeSetMethod"),
                AuditEntry.ExecutionType.BEFORE_EXECUTION,
                false);

        checkAuditEntry(
                auditLog.get(3),
                mongockDbState.get(3).getString("executionId"),
                null,
                mongockDbState.get(3).getString("changeId"),
                AuditEntry.Status.EXECUTED,
                mongockDbState.get(3).getString("changeLogClass"),
                mongockDbState.get(3).getString("changeSetMethod"),
                AuditEntry.ExecutionType.EXECUTION,
                false);

        checkAuditEntry(
                auditLog.get(4),
                auditLog.get(4).getExecutionId(),
                null,
                "mongock-local-legacy-importer-mongodb-3",
                AuditEntry.Status.EXECUTED,
                "io.flamingock.oss.driver.mongodb.sync.v4.internal.mongock.MongockLocalLegacyImporterChangeUnit",
                "execution",
                AuditEntry.ExecutionType.EXECUTION,
                false);


    }

    private void checkAuditEntry(AuditEntry actualAuditEntry,
                                 String expectedExecutionId,
                                 String expectedStageId,
                                 String expectedTaskId,
                                 AuditEntry.Status expectedStatus,
                                 String expectedClassName,
                                 String expectedMethodName,
                                 AuditEntry.ExecutionType expectedExecutionType,
                                 boolean expectedSystemChange) {
        assertEquals(expectedExecutionId, actualAuditEntry.getExecutionId());
        assertEquals(expectedStageId, actualAuditEntry.getStageId());
        assertEquals(expectedTaskId, actualAuditEntry.getTaskId());
        assertEquals(expectedStatus, actualAuditEntry.getState());
        assertEquals(expectedClassName, actualAuditEntry.getClassName());
        assertEquals(expectedMethodName, actualAuditEntry.getMethodName());
        assertEquals(expectedExecutionType, actualAuditEntry.getType());
        assertEquals(expectedSystemChange, actualAuditEntry.getSystemChange());

    }

}