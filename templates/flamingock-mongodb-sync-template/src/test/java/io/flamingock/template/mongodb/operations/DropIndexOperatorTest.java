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
import io.flamingock.template.mongodb.model.operator.DropIndexOperator;
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class DropIndexOperatorTest {

    private static final String DB_NAME = "test";
    private static final String COLLECTION_NAME = "indexTestCollection";
    private static final String INDEX_NAME = "email_index";

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
    @DisplayName("WHEN dropIndex operator is applied with index name THEN index is dropped")
    void dropIndexByNameTest() {
        mongoDatabase.createCollection(COLLECTION_NAME);
        mongoDatabase.getCollection(COLLECTION_NAME).createIndex(new Document("email", 1),
                new com.mongodb.client.model.IndexOptions().name(INDEX_NAME));
        assertTrue(indexExists(INDEX_NAME), "Index should exist before drop");

        MongoOperation operation = new MongoOperation();
        operation.setType("dropIndex");
        operation.setCollection(COLLECTION_NAME);
        Map<String, Object> params = new HashMap<>();
        params.put("indexName", INDEX_NAME);
        operation.setParameters(params);

        DropIndexOperator operator = new DropIndexOperator(mongoDatabase, operation);
        operator.apply(null);

        assertFalse(indexExists(INDEX_NAME), "Index should have been dropped");
    }

    @Test
    @DisplayName("WHEN dropIndex operator is applied with keys THEN index is dropped")
    void dropIndexByKeysTest() {
        mongoDatabase.createCollection(COLLECTION_NAME);
        mongoDatabase.getCollection(COLLECTION_NAME).createIndex(new Document("name", 1));
        assertTrue(indexExistsByKeys("name"), "Index should exist before drop");

        MongoOperation operation = new MongoOperation();
        operation.setType("dropIndex");
        operation.setCollection(COLLECTION_NAME);
        Map<String, Object> params = new HashMap<>();
        Map<String, Object> keys = new HashMap<>();
        keys.put("name", 1);
        params.put("keys", keys);
        operation.setParameters(params);

        DropIndexOperator operator = new DropIndexOperator(mongoDatabase, operation);
        operator.apply(null);

        assertFalse(indexExistsByKeys("name"), "Index should have been dropped");
    }

    private boolean indexExists(String indexName) {
        List<Document> indexes = mongoDatabase.getCollection(COLLECTION_NAME)
                .listIndexes()
                .into(new ArrayList<>());
        return indexes.stream().anyMatch(idx -> indexName.equals(idx.getString("name")));
    }

    private boolean indexExistsByKeys(String keyField) {
        List<Document> indexes = mongoDatabase.getCollection(COLLECTION_NAME)
                .listIndexes()
                .into(new ArrayList<>());
        return indexes.stream().anyMatch(idx -> {
            Document key = idx.get("key", Document.class);
            return key != null && key.containsKey(keyField);
        });
    }
}
