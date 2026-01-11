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
package io.flamingock.template.mongodb;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import io.flamingock.template.mongodb.model.MongoApplyPayload;
import io.flamingock.template.mongodb.model.MongoOperation;
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Template-level test for auto-rollback behavior in MongoChangeTemplate.
 *
 * <p>This test verifies that when an operation fails during template execution,
 * the auto-rollback mechanism correctly rolls back previously successful
 * operations in reverse order.</p>
 *
 */
@Testcontainers
class AutoRollbackTemplateTest {

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
        mongoDatabase.getCollection("auto_rollback_test").drop();
    }

    @Test
    @DisplayName("WHEN operation fails during template execution THEN auto-rollback is triggered for previous operations")
    void autoRollbackOnFailureTest() {
        assertFalse(collectionExists("auto_rollback_test"), "Collection should not exist before test");

        MongoApplyPayload payload = new MongoApplyPayload();
        List<MongoOperation> ops = new ArrayList<>();

        MongoOperation op1 = new MongoOperation();
        op1.setType("createCollection");
        op1.setCollection("auto_rollback_test");
        op1.setParameters(new HashMap<>());
        MongoOperation rollback1 = new MongoOperation();
        rollback1.setType("dropCollection");
        rollback1.setCollection("auto_rollback_test");
        rollback1.setParameters(new HashMap<>());
        op1.setRollback(rollback1);
        ops.add(op1);

        MongoOperation op2 = new MongoOperation();
        op2.setType("insert");
        op2.setCollection("auto_rollback_test");
        Map<String, Object> insertParams = new HashMap<>();
        List<Map<String, Object>> docs = new ArrayList<>();
        Map<String, Object> doc1 = new HashMap<>();
        doc1.put("name", "Test User 1");
        docs.add(doc1);
        Map<String, Object> doc2 = new HashMap<>();
        doc2.put("name", "Test User 2");
        docs.add(doc2);
        insertParams.put("documents", docs);
        op2.setParameters(insertParams);
        MongoOperation rollback2 = new MongoOperation();
        rollback2.setType("delete");
        rollback2.setCollection("auto_rollback_test");
        Map<String, Object> deleteParams = new HashMap<>();
        deleteParams.put("filter", new HashMap<>());
        rollback2.setParameters(deleteParams);
        op2.setRollback(rollback2);
        ops.add(op2);

        MongoOperation op3 = new MongoOperation();
        op3.setType("createCollection");
        op3.setCollection("auto_rollback_test");
        op3.setParameters(new HashMap<>());
        ops.add(op3);

        payload.setOperations(ops);

        MongoChangeTemplate template = new MongoChangeTemplate();
        template.setApplyPayload(payload);

        Exception thrown = assertThrows(Exception.class, () -> {
            template.apply(mongoDatabase, null);
        });

        assertTrue(thrown.getMessage().contains("already exists") ||
                   thrown.getCause().getMessage().contains("already exists"),
                "Should fail with 'already exists' error");

        assertFalse(collectionExists("auto_rollback_test"),
                "Collection should not exist after auto-rollback (dropCollection rollback executed)");
    }

    private boolean collectionExists(String collectionName) {
        return mongoDatabase.listCollectionNames()
                .into(new ArrayList<>())
                .contains(collectionName);
    }
}
