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
package io.flamingock.template.mongodb;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import io.flamingock.api.annotations.EnableFlamingock;
import io.flamingock.targetsystem.mongodb.sync.MongoDBSyncTargetSystem;
import io.flamingock.store.mongodb.sync.MongoDBSyncAuditStore;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.core.builder.FlamingockFactory;
import io.flamingock.template.mongodb.model.MongoOperation;
import org.bson.Document;

import java.util.Arrays;
import java.util.HashMap;
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

import static io.flamingock.internal.util.constants.CommunityPersistenceConstants.DEFAULT_AUDIT_STORE_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnableFlamingock(configFile = "flamingock/pipeline.yaml")
@Testcontainers
class MongoChangeTemplateTest {

    private static final String DB_NAME = "test";


    private static MongoClient mongoClient;

    private static MongoDatabase mongoDatabase;


    @Container
    public static final MongoDBContainer mongoDBContainer = new MongoDBContainer(DockerImageName.parse("mongo:6"));

    @BeforeAll
    static void beforeAll() {
        mongoClient = MongoClients.create(MongoClientSettings
                .builder()
                .applyConnectionString(new ConnectionString(mongoDBContainer.getConnectionString()))
                .build());
        mongoDatabase = mongoClient.getDatabase(DB_NAME);
    }

    @BeforeEach
    void setupEach() {
        mongoDatabase.getCollection(DEFAULT_AUDIT_STORE_NAME).drop();
        mongoDatabase.getCollection("users").drop();
        mongoDatabase.getCollection("products").drop();
        mongoDatabase.getCollection("rollbackTestCollection").drop();
    }


    @Test
    @DisplayName("WHEN mongodb template THEN runs fine IF Flamingock mongodb sync ce")
    void happyPath() {

        MongoDBSyncTargetSystem mongoDBSyncTargetSystem = new MongoDBSyncTargetSystem("mongodb", mongoClient, DB_NAME);
        FlamingockFactory.getCommunityBuilder()
                .setAuditStore(MongoDBSyncAuditStore.from(mongoDBSyncTargetSystem))
                .addTargetSystem(mongoDBSyncTargetSystem)
                .build()
                .run();


        List<Document> auditLog = mongoDatabase.getCollection(DEFAULT_AUDIT_STORE_NAME)
                .find()
                .into(new ArrayList<>());

        assertEquals(8, auditLog.size());

        assertEquals("create-users-collection-with-index", auditLog.get(0).getString("changeId"));
        assertEquals(AuditEntry.Status.STARTED.name(), auditLog.get(0).getString("state"));
        assertEquals("create-users-collection-with-index", auditLog.get(1).getString("changeId"));
        assertEquals(AuditEntry.Status.APPLIED.name(), auditLog.get(1).getString("state"));

        assertEquals("seed-users", auditLog.get(2).getString("changeId"));
        assertEquals(AuditEntry.Status.STARTED.name(), auditLog.get(2).getString("state"));
        assertEquals("seed-users", auditLog.get(3).getString("changeId"));
        assertEquals(AuditEntry.Status.APPLIED.name(), auditLog.get(3).getString("state"));

        assertEquals("multiple-operations-change", auditLog.get(4).getString("changeId"));
        assertEquals(AuditEntry.Status.STARTED.name(), auditLog.get(4).getString("state"));
        assertEquals("multiple-operations-change", auditLog.get(5).getString("changeId"));
        assertEquals(AuditEntry.Status.APPLIED.name(), auditLog.get(5).getString("state"));

        assertEquals("apply-and-rollback-test", auditLog.get(6).getString("changeId"));
        assertEquals(AuditEntry.Status.STARTED.name(), auditLog.get(6).getString("state"));
        assertEquals("apply-and-rollback-test", auditLog.get(7).getString("changeId"));
        assertEquals(AuditEntry.Status.APPLIED.name(), auditLog.get(7).getString("state"));

        // Verify for single operation
        List<Document> users = mongoDatabase.getCollection("users")
                .find()
                .into(new ArrayList<>());

        assertEquals(2, users.size());
        assertEquals("Admin", users.get(0).getString("name"));
        assertEquals("admin@company.com", users.get(0).getString("email"));
        assertEquals("superuser", users.get(0).getList("roles", String.class).get(0));

        assertEquals("Backup", users.get(1).getString("name"));
        assertEquals("backup@company.com", users.get(1).getString("email"));
        assertEquals("readonly", users.get(1).getList("roles", String.class).get(0));

        // Verify for multiple operation
        List<Document> products = mongoDatabase.getCollection("products")
                .find()
                .into(new ArrayList<>());

        assertEquals(3, products.size(), "Should have 3 products from multiple operations");
        assertEquals("Laptop", products.get(0).getString("name"));
        assertEquals("Keyboard", products.get(1).getString("name"));
        assertEquals("Mouse", products.get(2).getString("name"));

        List<Document> indexes = mongoDatabase.getCollection("products")
                .listIndexes()
                .into(new ArrayList<>());
        boolean categoryIndexExists = indexes.stream()
                .anyMatch(idx -> "category_index".equals(idx.getString("name")));
        assertTrue(categoryIndexExists, "Category index should exist on products collection");

        List<Document> rollbackItems = mongoDatabase.getCollection("rollbackTestCollection")
                .find()
                .into(new ArrayList<>());
        assertEquals(2, rollbackItems.size(), "Should have 2 items in rollbackTestCollection");
        assertEquals("Item1", rollbackItems.get(0).getString("name"));
        assertEquals("Item2", rollbackItems.get(1).getString("name"));

        List<Document> rollbackIndexes = mongoDatabase.getCollection("rollbackTestCollection")
                .listIndexes()
                .into(new ArrayList<>());
        boolean nameIndexExists = rollbackIndexes.stream()
                .anyMatch(idx -> "name_index".equals(idx.getString("name")));
        assertTrue(nameIndexExists, "Name index should exist on rollbackTestCollection");
    }

    @Test
    @DisplayName("WHEN rollback is invoked THEN multiple rollback operations execute in defined order")
    void rollbackWithMultipleOperations() {
        // First, set up the state by creating the collection and inserting data
        mongoDatabase.createCollection("rollbackTestCollection");
        mongoDatabase.getCollection("rollbackTestCollection").insertMany(Arrays.asList(
                new Document("name", "Item1").append("value", 100),
                new Document("name", "Item2").append("value", 200)
        ));
        mongoDatabase.getCollection("rollbackTestCollection").createIndex(
                new Document("name", 1),
                new com.mongodb.client.model.IndexOptions().name("name_index").unique(true)
        );

        assertTrue(collectionExists("rollbackTestCollection"), "Collection should exist before rollback");
        assertEquals(2, mongoDatabase.getCollection("rollbackTestCollection").countDocuments(),
                "Should have 2 documents before rollback");

        MongoChangeTemplate template = new MongoChangeTemplate();
        template.setChangeId("apply-and-rollback-test");
        template.setTransactional(false);

        // Set rollback payload - should execute in order:
        // 1. Drop index
        // 2. Delete all documents
        // 3. Drop collection
        List<MongoOperation> rollbackOps = new ArrayList<>();

        MongoOperation dropIndexOp = new MongoOperation();
        dropIndexOp.setType("dropIndex");
        dropIndexOp.setCollection("rollbackTestCollection");
        HashMap<String, Object> dropIndexParams = new HashMap<>();
        dropIndexParams.put("indexName", "name_index");
        dropIndexOp.setParameters(dropIndexParams);
        rollbackOps.add(dropIndexOp);

        MongoOperation deleteOp = new MongoOperation();
        deleteOp.setType("delete");
        deleteOp.setCollection("rollbackTestCollection");
        HashMap<String, Object> deleteParams = new HashMap<>();
        deleteParams.put("filter", new HashMap<>());
        deleteOp.setParameters(deleteParams);
        rollbackOps.add(deleteOp);

        MongoOperation dropCollectionOp = new MongoOperation();
        dropCollectionOp.setType("dropCollection");
        dropCollectionOp.setCollection("rollbackTestCollection");
        rollbackOps.add(dropCollectionOp);

        template.setRollbackPayload(rollbackOps);

        template.rollback(mongoDatabase, null);

        assertFalse(collectionExists("rollbackTestCollection"),
                "Collection should not exist after rollback");
    }

    private boolean collectionExists(String collectionName) {
        return mongoDatabase.listCollectionNames()
                .into(new ArrayList<>())
                .contains(collectionName);
    }

}
