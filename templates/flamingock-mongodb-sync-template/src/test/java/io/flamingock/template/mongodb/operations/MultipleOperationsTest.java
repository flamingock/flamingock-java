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
import io.flamingock.template.mongodb.model.MongoApplyPayload;
import io.flamingock.template.mongodb.model.MongoOperation;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class MultipleOperationsTest {

    private static final String DB_NAME = "test";
    private static final String COLLECTION_NAME = "multiOpsCollection";

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
    }

    @Test
    @DisplayName("WHEN multiple operations are executed THEN all operations complete successfully")
    void multipleOperationsTest() {
        MongoApplyPayload payload = new MongoApplyPayload();
        List<MongoOperation> ops = new ArrayList<>();

        // Op 1: Create collection
        MongoOperation op1 = new MongoOperation();
        op1.setType("createCollection");
        op1.setCollection(COLLECTION_NAME);
        op1.setParameters(new HashMap<>());
        ops.add(op1);

        // Op 2: Insert documents
        MongoOperation op2 = new MongoOperation();
        op2.setType("insert");
        op2.setCollection(COLLECTION_NAME);
        Map<String, Object> insertParams = new HashMap<>();
        List<Map<String, Object>> documents = new ArrayList<>();
        Map<String, Object> doc1 = new HashMap<>();
        doc1.put("name", "User1");
        doc1.put("email", "user1@example.com");
        documents.add(doc1);
        Map<String, Object> doc2 = new HashMap<>();
        doc2.put("name", "User2");
        doc2.put("email", "user2@example.com");
        documents.add(doc2);
        insertParams.put("documents", documents);
        op2.setParameters(insertParams);
        ops.add(op2);

        // Op 3: Create index
        MongoOperation op3 = new MongoOperation();
        op3.setType("createIndex");
        op3.setCollection(COLLECTION_NAME);
        Map<String, Object> indexParams = new HashMap<>();
        Map<String, Object> keys = new HashMap<>();
        keys.put("email", 1);
        indexParams.put("keys", keys);
        Map<String, Object> options = new HashMap<>();
        options.put("unique", true);
        indexParams.put("options", options);
        op3.setParameters(indexParams);
        ops.add(op3);

        payload.setOperations(ops);

        for (MongoOperation op : payload.getOperations()) {
            op.getOperator(mongoDatabase).apply(null);
        }

        assertTrue(collectionExists(COLLECTION_NAME), "Collection should exist");
        assertEquals(2, getDocumentCount(), "Should have 2 documents");
        assertTrue(indexExists("email"), "Index on email should exist");
    }

    @Test
    @DisplayName("WHEN single operation format is used THEN backward compatibility works")
    void backwardCompatibilitySingleOperationTest() {
        MongoApplyPayload payload = new MongoApplyPayload();
        payload.setType("createCollection");
        payload.setCollection(COLLECTION_NAME);
        payload.setParameters(new HashMap<>());

        List<MongoOperation> ops = payload.getOperations();
        assertEquals(1, ops.size(), "Should have exactly one operation");
        assertEquals("createCollection", ops.get(0).getType());
        assertEquals(COLLECTION_NAME, ops.get(0).getCollection());

        for (MongoOperation op : ops) {
            op.getOperator(mongoDatabase).apply(null);
        }

        assertTrue(collectionExists(COLLECTION_NAME), "Collection should exist");
    }

    @Test
    @DisplayName("WHEN payload has no operations THEN empty list is returned")
    void emptyPayloadTest() {
        MongoApplyPayload payload = new MongoApplyPayload();

        List<MongoOperation> ops = payload.getOperations();
        assertTrue(ops.isEmpty(), "Should return empty list");
    }

    @Test
    @DisplayName("WHEN operations list is set but type is also set THEN operations list takes precedence")
    void operationsListTakesPrecedenceTest() {
        MongoApplyPayload payload = new MongoApplyPayload();

        // Backward compatibility
        payload.setType("dropCollection");
        payload.setCollection("someOtherCollection");

        List<MongoOperation> ops = new ArrayList<>();
        MongoOperation op = new MongoOperation();
        op.setType("createCollection");
        op.setCollection(COLLECTION_NAME);
        op.setParameters(new HashMap<>());
        ops.add(op);
        payload.setOperations(ops);

        List<MongoOperation> result = payload.getOperations();
        assertEquals(1, result.size());
        assertEquals("createCollection", result.get(0).getType());
        assertEquals(COLLECTION_NAME, result.get(0).getCollection());
    }

    private boolean collectionExists(String collectionName) {
        return mongoDatabase.listCollectionNames()
                .into(new ArrayList<>())
                .contains(collectionName);
    }

    private long getDocumentCount() {
        return mongoDatabase.getCollection(COLLECTION_NAME).countDocuments();
    }

    private boolean indexExists(String keyField) {
        List<Document> indexes = mongoDatabase.getCollection(COLLECTION_NAME)
                .listIndexes()
                .into(new ArrayList<>());
        return indexes.stream().anyMatch(idx -> {
            Document key = idx.get("key", Document.class);
            return key != null && key.containsKey(keyField);
        });
    }
}
