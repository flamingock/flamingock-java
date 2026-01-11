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

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class PerOperationRollbackTest {

    private static final String DB_NAME = "test";
    private static final String COLLECTION_NAME = "rollbackTestCollection";

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
        mongoDatabase.getCollection("failCollection").drop();
    }

    @Test
    @DisplayName("WHEN all operations succeed THEN no rollback is triggered")
    void successfulApplyNoRollbackTest() {
        MongoApplyPayload payload = new MongoApplyPayload();
        List<MongoOperation> ops = new ArrayList<>();

        MongoOperation op1 = createOperation("createCollection", COLLECTION_NAME);
        op1.setRollback(createOperation("dropCollection", COLLECTION_NAME));
        ops.add(op1);

        MongoOperation op2 = createInsertOperation(COLLECTION_NAME, "TestUser");
        MongoOperation rollback2 = new MongoOperation();
        rollback2.setType("delete");
        rollback2.setCollection(COLLECTION_NAME);
        Map<String, Object> deleteParams = new HashMap<>();
        deleteParams.put("filter", new HashMap<>());
        rollback2.setParameters(deleteParams);
        op2.setRollback(rollback2);
        ops.add(op2);

        payload.setOperations(ops);

        List<MongoOperation> successfulOps = new ArrayList<>();
        for (MongoOperation op : payload.getOperations()) {
            op.getOperator(mongoDatabase).apply(null);
            successfulOps.add(op);
        }

        // All operations ok
        assertTrue(collectionExists(COLLECTION_NAME), "Collection should exist");
        assertEquals(1, getDocumentCount(COLLECTION_NAME), "Should have 1 document");
    }

    @Test
    @DisplayName("WHEN operation fails THEN previous operations are rolled back in reverse order")
    void partialFailureAutoRollbackTest() {
        mongoDatabase.createCollection(COLLECTION_NAME);
        mongoDatabase.getCollection(COLLECTION_NAME).insertOne(new Document("name", "Existing"));

        MongoApplyPayload payload = new MongoApplyPayload();
        List<MongoOperation> ops = new ArrayList<>();

        MongoOperation op1 = createInsertOperation(COLLECTION_NAME, "NewUser");
        MongoOperation rollback1 = new MongoOperation();
        rollback1.setType("delete");
        rollback1.setCollection(COLLECTION_NAME);
        Map<String, Object> deleteParams = new HashMap<>();
        Map<String, Object> filter = new HashMap<>();
        filter.put("name", "NewUser");
        deleteParams.put("filter", filter);
        rollback1.setParameters(deleteParams);
        op1.setRollback(rollback1);
        ops.add(op1);

        // Will fail, already exists
        MongoOperation op2 = createOperation("createCollection", COLLECTION_NAME);
        ops.add(op2);

        payload.setOperations(ops);

        List<MongoOperation> successfulOps = new ArrayList<>();
        Exception caughtException = null;

        for (MongoOperation op : payload.getOperations()) {
            try {
                op.getOperator(mongoDatabase).apply(null);
                successfulOps.add(op);
            } catch (Exception e) {
                caughtException = e;
                for (int i = successfulOps.size() - 1; i >= 0; i--) {
                    MongoOperation successfulOp = successfulOps.get(i);
                    if (successfulOp.getRollback() != null) {
                        successfulOp.getRollback().getOperator(mongoDatabase).apply(null);
                    }
                }
                break;
            }
        }

        assertNotNull(caughtException, "Should have caught an exception");
        assertTrue(collectionExists(COLLECTION_NAME), "Collection should still exist");

        List<Document> docs = mongoDatabase.getCollection(COLLECTION_NAME)
                .find()
                .into(new ArrayList<>());
        assertEquals(1, docs.size(), "Should only have the original document");
        assertEquals("Existing", docs.get(0).getString("name"), "Only original document should remain");
    }

    @Test
    @DisplayName("WHEN rollback is executed THEN operations are rolled back in reverse order")
    void rollbackExecutesInReverseOrderTest() {
        mongoDatabase.createCollection(COLLECTION_NAME);
        List<Document> docs = new ArrayList<>();
        docs.add(new Document("name", "User1"));
        docs.add(new Document("name", "User2"));
        docs.add(new Document("name", "User3"));
        mongoDatabase.getCollection(COLLECTION_NAME).insertMany(docs);

        MongoApplyPayload payload = new MongoApplyPayload();
        List<MongoOperation> ops = new ArrayList<>();

        MongoOperation op1 = createInsertOperation(COLLECTION_NAME, "User1");
        MongoOperation rollback1 = createDeleteOperation(COLLECTION_NAME, "User1");
        op1.setRollback(rollback1);
        ops.add(op1);

        MongoOperation op2 = createInsertOperation(COLLECTION_NAME, "User2");
        MongoOperation rollback2 = createDeleteOperation(COLLECTION_NAME, "User2");
        op2.setRollback(rollback2);
        ops.add(op2);

        MongoOperation op3 = createInsertOperation(COLLECTION_NAME, "User3");
        MongoOperation rollback3 = createDeleteOperation(COLLECTION_NAME, "User3");
        op3.setRollback(rollback3);
        ops.add(op3);

        payload.setOperations(ops);

        List<MongoOperation> operations = payload.getOperations();
        for (int i = operations.size() - 1; i >= 0; i--) {
            MongoOperation op = operations.get(i);
            if (op.getRollback() != null) {
                op.getRollback().getOperator(mongoDatabase).apply(null);
            }
        }

        assertEquals(0, getDocumentCount(COLLECTION_NAME), "All documents should be deleted");
    }

    @Test
    @DisplayName("WHEN operation has no rollback THEN it is skipped during rollback")
    void missingRollbackIsSkippedTest() {
        mongoDatabase.createCollection(COLLECTION_NAME);

        MongoApplyPayload payload = new MongoApplyPayload();
        List<MongoOperation> ops = new ArrayList<>();

        MongoOperation op1 = createInsertOperation(COLLECTION_NAME, "User1");
        op1.setRollback(createDeleteOperation(COLLECTION_NAME, "User1"));
        ops.add(op1);

        MongoOperation op2 = createInsertOperation(COLLECTION_NAME, "User2");
        ops.add(op2);

        MongoOperation op3 = createInsertOperation(COLLECTION_NAME, "User3");
        op3.setRollback(createDeleteOperation(COLLECTION_NAME, "User3"));
        ops.add(op3);

        payload.setOperations(ops);

        for (MongoOperation op : payload.getOperations()) {
            op.getOperator(mongoDatabase).apply(null);
        }

        assertEquals(3, getDocumentCount(COLLECTION_NAME), "Should have 3 documents");

        List<MongoOperation> operations = payload.getOperations();
        for (int i = operations.size() - 1; i >= 0; i--) {
            MongoOperation op = operations.get(i);
            if (op.getRollback() != null) {
                op.getRollback().getOperator(mongoDatabase).apply(null);
            }
        }

        List<Document> docs = mongoDatabase.getCollection(COLLECTION_NAME)
                .find()
                .into(new ArrayList<>());
        assertEquals(1, docs.size(), "Only User2 should remain");
        assertEquals("User2", docs.get(0).getString("name"));
    }

    @Test
    @DisplayName("WHEN MongoOperation has rollback defined THEN getRollback returns it")
    void getRollbackReturnsDefinedRollbackTest() {
        MongoOperation op = createOperation("createCollection", COLLECTION_NAME);
        MongoOperation rollback = createOperation("dropCollection", COLLECTION_NAME);
        op.setRollback(rollback);

        assertEquals(rollback, op.getRollback());
        assertEquals("dropCollection", op.getRollback().getType());
        assertEquals(COLLECTION_NAME, op.getRollback().getCollection());
    }

    private MongoOperation createOperation(String type, String collection) {
        MongoOperation op = new MongoOperation();
        op.setType(type);
        op.setCollection(collection);
        op.setParameters(new HashMap<>());
        return op;
    }

    private MongoOperation createInsertOperation(String collection, String userName) {
        MongoOperation op = new MongoOperation();
        op.setType("insert");
        op.setCollection(collection);
        Map<String, Object> params = new HashMap<>();
        List<Map<String, Object>> docs = new ArrayList<>();
        Map<String, Object> doc = new HashMap<>();
        doc.put("name", userName);
        docs.add(doc);
        params.put("documents", docs);
        op.setParameters(params);
        return op;
    }

    private MongoOperation createDeleteOperation(String collection, String userName) {
        MongoOperation op = new MongoOperation();
        op.setType("delete");
        op.setCollection(collection);
        Map<String, Object> params = new HashMap<>();
        Map<String, Object> filter = new HashMap<>();
        filter.put("name", userName);
        params.put("filter", filter);
        op.setParameters(params);
        return op;
    }

    private boolean collectionExists(String collectionName) {
        return mongoDatabase.listCollectionNames()
                .into(new ArrayList<>())
                .contains(collectionName);
    }

    private long getDocumentCount(String collectionName) {
        return mongoDatabase.getCollection(collectionName).countDocuments();
    }
}
