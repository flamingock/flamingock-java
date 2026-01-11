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
package io.flamingock.template.mongodb.operations;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import io.flamingock.template.mongodb.model.MongoOperation;
import io.flamingock.template.mongodb.model.operator.UpdateOperator;
import org.bson.Document;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class UpdateOperatorTest {

    private static final String DB_NAME = "test";
    private static final String COLLECTION_NAME = "updateTestCollection";

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
        mongoDatabase.getCollection(COLLECTION_NAME).drop();
        mongoDatabase.createCollection(COLLECTION_NAME);
        List<Document> docs = new ArrayList<>();
        docs.add(new Document("name", "Alice").append("role", "admin").append("score", 100));
        docs.add(new Document("name", "Bob").append("role", "user").append("score", 80));
        docs.add(new Document("name", "Charlie").append("role", "user").append("score", 90));
        docs.add(new Document("name", "Diana").append("role", "admin").append("score", 95));
        mongoDatabase.getCollection(COLLECTION_NAME).insertMany(docs);
    }

    @Test
    @DisplayName("WHEN updateOne is applied THEN only first matching document is updated")
    void updateOneTest() {
        MongoOperation operation = new MongoOperation();
        operation.setType("update");
        operation.setCollection(COLLECTION_NAME);

        Map<String, Object> params = new HashMap<>();
        Map<String, Object> filter = new HashMap<>();
        filter.put("role", "user");
        params.put("filter", filter);

        Map<String, Object> update = new HashMap<>();
        Map<String, Object> setFields = new HashMap<>();
        setFields.put("role", "guest");
        update.put("$set", setFields);
        params.put("update", update);
        // multi defaults to false
        operation.setParameters(params);

        UpdateOperator operator = new UpdateOperator(mongoDatabase, operation);
        operator.apply(null);

        // Only one user should be updated to guest
        long guestCount = mongoDatabase.getCollection(COLLECTION_NAME)
                .countDocuments(new Document("role", "guest"));
        assertEquals(1, guestCount, "Only one document should be updated");

        long userCount = mongoDatabase.getCollection(COLLECTION_NAME)
                .countDocuments(new Document("role", "user"));
        assertEquals(1, userCount, "One user document should remain");
    }

    @Test
    @DisplayName("WHEN updateMany is applied THEN all matching documents are updated")
    void updateManyTest() {
        MongoOperation operation = new MongoOperation();
        operation.setType("update");
        operation.setCollection(COLLECTION_NAME);

        Map<String, Object> params = new HashMap<>();
        Map<String, Object> filter = new HashMap<>();
        filter.put("role", "user");
        params.put("filter", filter);

        Map<String, Object> update = new HashMap<>();
        Map<String, Object> setFields = new HashMap<>();
        setFields.put("role", "guest");
        update.put("$set", setFields);
        params.put("update", update);
        params.put("multi", true);
        operation.setParameters(params);

        UpdateOperator operator = new UpdateOperator(mongoDatabase, operation);
        operator.apply(null);

        long guestCount = mongoDatabase.getCollection(COLLECTION_NAME)
                .countDocuments(new Document("role", "guest"));
        assertEquals(2, guestCount, "Both user documents should be updated to guest");

        long userCount = mongoDatabase.getCollection(COLLECTION_NAME)
                .countDocuments(new Document("role", "user"));
        assertEquals(0, userCount, "No user documents should remain");
    }

    @Test
    @DisplayName("WHEN update with upsert option and no match THEN new document is created")
    void updateWithUpsertTest() {
        long initialCount = getDocumentCount();

        MongoOperation operation = new MongoOperation();
        operation.setType("update");
        operation.setCollection(COLLECTION_NAME);

        Map<String, Object> params = new HashMap<>();
        Map<String, Object> filter = new HashMap<>();
        filter.put("name", "NewUser");
        params.put("filter", filter);

        Map<String, Object> update = new HashMap<>();
        Map<String, Object> setFields = new HashMap<>();
        setFields.put("name", "NewUser");
        setFields.put("role", "new");
        update.put("$set", setFields);
        params.put("update", update);

        Map<String, Object> options = new HashMap<>();
        options.put("upsert", true);
        params.put("options", options);

        operation.setParameters(params);

        UpdateOperator operator = new UpdateOperator(mongoDatabase, operation);
        operator.apply(null);

        assertEquals(initialCount + 1, getDocumentCount(), "New document should be created via upsert");

        Document newDoc = mongoDatabase.getCollection(COLLECTION_NAME)
                .find(new Document("name", "NewUser"))
                .first();
        assertNotNull(newDoc, "Upserted document should exist");
        assertEquals("new", newDoc.getString("role"));
    }

    @Test
    @DisplayName("WHEN update with $inc operator THEN numeric field is incremented")
    void updateWithIncTest() {
        MongoOperation operation = new MongoOperation();
        operation.setType("update");
        operation.setCollection(COLLECTION_NAME);

        Map<String, Object> params = new HashMap<>();
        Map<String, Object> filter = new HashMap<>();
        filter.put("name", "Alice");
        params.put("filter", filter);

        Map<String, Object> update = new HashMap<>();
        Map<String, Object> incFields = new HashMap<>();
        incFields.put("score", 10);
        update.put("$inc", incFields);
        params.put("update", update);
        operation.setParameters(params);

        UpdateOperator operator = new UpdateOperator(mongoDatabase, operation);
        operator.apply(null);

        Document alice = mongoDatabase.getCollection(COLLECTION_NAME)
                .find(new Document("name", "Alice"))
                .first();
        assertNotNull(alice);
        assertEquals(110, alice.getInteger("score"), "Score should be incremented by 10");
    }

    @Test
    @DisplayName("WHEN update with non-matching filter THEN no documents are updated")
    void updateWithNonMatchingFilterTest() {
        MongoOperation operation = new MongoOperation();
        operation.setType("update");
        operation.setCollection(COLLECTION_NAME);

        Map<String, Object> params = new HashMap<>();
        Map<String, Object> filter = new HashMap<>();
        filter.put("name", "NonExistent");
        params.put("filter", filter);

        Map<String, Object> update = new HashMap<>();
        Map<String, Object> setFields = new HashMap<>();
        setFields.put("role", "changed");
        update.put("$set", setFields);
        params.put("update", update);
        operation.setParameters(params);

        UpdateOperator operator = new UpdateOperator(mongoDatabase, operation);
        operator.apply(null);

        long changedCount = mongoDatabase.getCollection(COLLECTION_NAME)
                .countDocuments(new Document("role", "changed"));
        assertEquals(0, changedCount, "No documents should be updated");
    }

    @Test
    @DisplayName("WHEN update with $unset operator THEN field is removed")
    void updateWithUnsetTest() {
        MongoOperation operation = new MongoOperation();
        operation.setType("update");
        operation.setCollection(COLLECTION_NAME);

        Map<String, Object> params = new HashMap<>();
        Map<String, Object> filter = new HashMap<>();
        filter.put("name", "Alice");
        params.put("filter", filter);

        Map<String, Object> update = new HashMap<>();
        Map<String, Object> unsetFields = new HashMap<>();
        unsetFields.put("score", "");
        update.put("$unset", unsetFields);
        params.put("update", update);
        operation.setParameters(params);

        UpdateOperator operator = new UpdateOperator(mongoDatabase, operation);
        operator.apply(null);

        Document alice = mongoDatabase.getCollection(COLLECTION_NAME)
                .find(new Document("name", "Alice"))
                .first();
        assertNotNull(alice);
        assertNull(alice.getInteger("score"), "Score field should be removed");
    }

    private long getDocumentCount() {
        return mongoDatabase.getCollection(COLLECTION_NAME).countDocuments();
    }
}
