/*
 * Copyright 2026 Flamingock (https://www.flamingock.io)
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
package io.flamingock.importer.mongock.mongodb.reactive;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.mongodb.reactivestreams.client.MongoDatabase;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.reactive.util.PublisherSync;
import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.Date;
import java.util.List;

@Testcontainers
class MongockImporterMongoDBReactiveTest {

    private static final String DB_NAME = "importerTest";
    private static final String LEGACY_COLLECTION = "mongockChangeLog";

    private static MongoDBContainer mongoDBContainer;
    private static MongoClient mongoClient;
    private static MongoDatabase database;

    @BeforeAll
    static void setUpContainer() {
        mongoDBContainer = new MongoDBContainer(DockerImageName.parse("mongo:8.2.9"));
        mongoDBContainer.start();
        MongoClientSettings settings = MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(mongoDBContainer.getReplicaSetUrl()))
                .build();
        mongoClient = MongoClients.create(settings);
        database = mongoClient.getDatabase(DB_NAME);
    }

    @AfterAll
    static void tearDownContainer() {
        mongoClient.close();
        mongoDBContainer.stop();
    }

    @BeforeEach
    void setUp() {
        PublisherSync.complete(database.getCollection(LEGACY_COLLECTION).drop());
    }

    @AfterEach
    void tearDown() {
        PublisherSync.complete(database.getCollection(LEGACY_COLLECTION).drop());
    }

    @Test
    @DisplayName("Should map an EXECUTED legacy entry to an AuditEntry")
    void shouldMapExecutedEntry() {
        seed(document("users-initialization", "EXECUTED", "EXECUTION", "pretend-mongock-run"));

        MongockImporterMongoDBReactive importer = new MongockImporterMongoDBReactive(database, LEGACY_COLLECTION);
        List<AuditEntry> history = importer.getAuditHistory();

        Assertions.assertEquals(1, history.size());
        AuditEntry entry = history.get(0);
        Assertions.assertEquals("users-initialization", entry.getChangeId());
        Assertions.assertEquals(AuditEntry.Status.APPLIED, entry.getState());
        Assertions.assertEquals(AuditEntry.ChangeType.MONGOCK_EXECUTION, entry.getType());
        Assertions.assertEquals("pretend-mongock-run", entry.getExecutionId());
    }

    @Test
    @DisplayName("Should skip an IGNORED legacy entry")
    void shouldSkipIgnoredEntry() {
        seed(document("users-initialization", "EXECUTED", "EXECUTION", "pretend-mongock-run"));
        seed(document("ghost-extra", "IGNORED", "EXECUTION", null));

        MongockImporterMongoDBReactive importer = new MongockImporterMongoDBReactive(database, LEGACY_COLLECTION);
        List<AuditEntry> history = importer.getAuditHistory();

        Assertions.assertEquals(1, history.size());
        Assertions.assertEquals("users-initialization", history.get(0).getChangeId());
    }

    @Test
    @DisplayName("Should return an empty list when the origin collection is empty")
    void shouldReturnEmptyListForEmptyOrigin() {
        MongockImporterMongoDBReactive importer = new MongockImporterMongoDBReactive(database, LEGACY_COLLECTION);
        Assertions.assertTrue(importer.getAuditHistory().isEmpty());
    }

    private void seed(Document document) {
        PublisherSync.complete(database.getCollection(LEGACY_COLLECTION).insertOne(document));
    }

    private static Document document(String changeId, String state, String type, String executionId) {
        Document doc = new Document()
                .append("changeId", changeId)
                .append("state", state)
                .append("type", type)
                .append("author", "jhipster")
                .append("changeLogClass", "com.example.config.dbmigrations._0001__InitialSetupMigration")
                .append("changeSetMethod", "changeSet")
                .append("executionMillis", 12L)
                .append("executionHostName", "test")
                .append("timestamp", Date.from(Instant.now()));
        if (executionId != null) {
            doc.append("executionId", executionId);
        }
        return doc;
    }
}
