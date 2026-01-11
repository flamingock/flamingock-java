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
import io.flamingock.template.mongodb.model.operator.DeleteOperator;
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
import static org.junit.jupiter.api.Assertions.assertNull;

@Testcontainers
class DeleteOperatorTest {

    private static final String DB_NAME = "test";
    private static final String COLLECTION_NAME = "deleteTestCollection";

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
        docs.add(new Document("name", "Alice").append("role", "admin"));
        docs.add(new Document("name", "Bob").append("role", "user"));
        docs.add(new Document("name", "Charlie").append("role", "user"));
        docs.add(new Document("name", "Diana").append("role", "admin"));
        mongoDatabase.getCollection(COLLECTION_NAME).insertMany(docs);
    }

    @Test
    @DisplayName("WHEN delete operator is applied with specific filter THEN matching documents are deleted")
    void deleteWithFilterTest() {
        assertEquals(4, getDocumentCount(), "Collection should have 4 documents before delete");

        MongoOperation operation = new MongoOperation();
        operation.setType("delete");
        operation.setCollection(COLLECTION_NAME);

        Map<String, Object> params = new HashMap<>();
        Map<String, Object> filter = new HashMap<>();
        filter.put("role", "user");
        params.put("filter", filter);
        operation.setParameters(params);

        DeleteOperator operator = new DeleteOperator(mongoDatabase, operation);
        operator.apply(null);

        assertEquals(2, getDocumentCount(), "Collection should have 2 documents after delete");

        List<Document> remainingDocs = mongoDatabase.getCollection(COLLECTION_NAME)
                .find()
                .into(new ArrayList<>());

        for (Document doc : remainingDocs) {
            assertEquals("admin", doc.getString("role"), "Only admin documents should remain");
        }
    }

    @Test
    @DisplayName("WHEN delete operator is applied with empty filter THEN all documents are deleted")
    void deleteAllWithEmptyFilterTest() {
        assertEquals(4, getDocumentCount(), "Collection should have 4 documents before delete");

        MongoOperation operation = new MongoOperation();
        operation.setType("delete");
        operation.setCollection(COLLECTION_NAME);

        Map<String, Object> params = new HashMap<>();
        params.put("filter", new HashMap<>());
        operation.setParameters(params);

        DeleteOperator operator = new DeleteOperator(mongoDatabase, operation);
        operator.apply(null);

        assertEquals(0, getDocumentCount(), "Collection should be empty after delete with empty filter");
    }

    @Test
    @DisplayName("WHEN delete operator is applied with filter matching single document THEN only that document is deleted")
    void deleteSingleDocumentTest() {
        assertEquals(4, getDocumentCount(), "Collection should have 4 documents before delete");

        MongoOperation operation = new MongoOperation();
        operation.setType("delete");
        operation.setCollection(COLLECTION_NAME);

        Map<String, Object> params = new HashMap<>();
        Map<String, Object> filter = new HashMap<>();
        filter.put("name", "Alice");
        params.put("filter", filter);
        operation.setParameters(params);

        DeleteOperator operator = new DeleteOperator(mongoDatabase, operation);
        operator.apply(null);

        assertEquals(3, getDocumentCount(), "Collection should have 3 documents after delete");

        Document alice = mongoDatabase.getCollection(COLLECTION_NAME)
                .find(new Document("name", "Alice"))
                .first();
        assertNull(alice, "Alice should be deleted");
    }

    @Test
    @DisplayName("WHEN delete operator is applied with non-matching filter THEN no documents are deleted")
    void deleteWithNonMatchingFilterTest() {
        assertEquals(4, getDocumentCount(), "Collection should have 4 documents before delete");

        MongoOperation operation = new MongoOperation();
        operation.setType("delete");
        operation.setCollection(COLLECTION_NAME);

        Map<String, Object> params = new HashMap<>();
        Map<String, Object> filter = new HashMap<>();
        filter.put("name", "NonExistent");
        params.put("filter", filter);
        operation.setParameters(params);

        DeleteOperator operator = new DeleteOperator(mongoDatabase, operation);
        operator.apply(null);

        assertEquals(4, getDocumentCount(), "Collection should still have 4 documents");
    }

    private long getDocumentCount() {
        return mongoDatabase.getCollection(COLLECTION_NAME).countDocuments();
    }
}
