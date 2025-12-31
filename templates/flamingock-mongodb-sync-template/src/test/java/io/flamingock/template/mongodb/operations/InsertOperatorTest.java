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
import io.flamingock.template.mongodb.model.operator.InsertOperator;
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Testcontainers
class InsertOperatorTest {

    private static final String DB_NAME = "test";
    private static final String COLLECTION_NAME = "insertTestCollection";

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
    }

    @Test
    @DisplayName("WHEN insert operator is applied with one document THEN document is inserted")
    void insertOneDocumentTest() {
        // Verify collection is empty before
        assertEquals(0, getDocumentCount(), "Collection should be empty before insert");

        // Create the operation
        MongoOperation operation = new MongoOperation();
        operation.setType("insert");
        operation.setCollection(COLLECTION_NAME);

        Map<String, Object> params = new HashMap<>();
        List<Map<String, Object>> documents = new ArrayList<>();
        Map<String, Object> doc = new HashMap<>();
        doc.put("name", "John Doe");
        doc.put("email", "john@example.com");
        documents.add(doc);
        params.put("documents", documents);
        operation.setParameters(params);

        // Execute the operator
        InsertOperator operator = new InsertOperator(mongoDatabase, operation);
        operator.apply(null);

        // Verify
        assertEquals(1, getDocumentCount(), "Collection should have one document");
        Document inserted = mongoDatabase.getCollection(COLLECTION_NAME).find().first();
        assertEquals("John Doe", inserted.getString("name"));
        assertEquals("john@example.com", inserted.getString("email"));
    }

    @Test
    @DisplayName("WHEN insert operator is applied with multiple documents THEN documents are inserted")
    void insertManyDocumentsTest() {
        assertEquals(0, getDocumentCount(), "Collection should be empty before insert");

        MongoOperation operation = new MongoOperation();
        operation.setType("insert");
        operation.setCollection(COLLECTION_NAME);

        Map<String, Object> params = new HashMap<>();
        List<Map<String, Object>> documents = new ArrayList<>();

        Map<String, Object> doc1 = new HashMap<>();
        doc1.put("name", "Alice");
        doc1.put("email", "alice@example.com");
        documents.add(doc1);

        Map<String, Object> doc2 = new HashMap<>();
        doc2.put("name", "Bob");
        doc2.put("email", "bob@example.com");
        documents.add(doc2);

        Map<String, Object> doc3 = new HashMap<>();
        doc3.put("name", "Charlie");
        doc3.put("email", "charlie@example.com");
        documents.add(doc3);

        params.put("documents", documents);
        operation.setParameters(params);

        InsertOperator operator = new InsertOperator(mongoDatabase, operation);
        operator.apply(null);

        assertEquals(3, getDocumentCount(), "Collection should have three documents");

        List<Document> insertedDocs = mongoDatabase.getCollection(COLLECTION_NAME)
                .find()
                .into(new ArrayList<>());

        List<String> names = Arrays.asList("Alice", "Bob", "Charlie");
        for (Document doc : insertedDocs) {
            assertTrue(names.contains(doc.getString("name")), "Document name should be in expected list");
        }
    }

    @Test
    @DisplayName("WHEN insert operator is applied with empty documents THEN nothing is inserted")
    void insertEmptyDocumentsTest() {
        assertEquals(0, getDocumentCount(), "Collection should be empty before insert");

        MongoOperation operation = new MongoOperation();
        operation.setType("insert");
        operation.setCollection(COLLECTION_NAME);

        Map<String, Object> params = new HashMap<>();
        params.put("documents", new ArrayList<>());
        operation.setParameters(params);

        InsertOperator operator = new InsertOperator(mongoDatabase, operation);
        operator.apply(null);

        assertEquals(0, getDocumentCount(), "Collection should still be empty");
    }

    private long getDocumentCount() {
        return mongoDatabase.getCollection(COLLECTION_NAME).countDocuments();
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
