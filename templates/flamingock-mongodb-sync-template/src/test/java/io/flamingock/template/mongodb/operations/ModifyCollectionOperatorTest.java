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
import io.flamingock.template.mongodb.model.operator.ModifyCollectionOperator;
import org.bson.Document;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@Testcontainers
class ModifyCollectionOperatorTest {

    private static final String DB_NAME = "test";
    private static final String COLLECTION_NAME = "modifyTestCollection";

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
    @DisplayName("WHEN modifyCollection operator is applied THEN collection validation is set")
    void modifyCollectionTest() {
        mongoDatabase.createCollection(COLLECTION_NAME);

        MongoOperation operation = new MongoOperation();
        operation.setType("modifyCollection");
        operation.setCollection(COLLECTION_NAME);

        Map<String, Object> params = new HashMap<>();
        Map<String, Object> validator = new HashMap<>();
        Map<String, Object> jsonSchema = new HashMap<>();
        jsonSchema.put("bsonType", "object");
        validator.put("$jsonSchema", jsonSchema);
        params.put("validator", validator);
        params.put("validationLevel", "moderate");
        params.put("validationAction", "warn");
        operation.setParameters(params);

        ModifyCollectionOperator operator = new ModifyCollectionOperator(mongoDatabase, operation);
        operator.apply(null);

        Document collectionInfo = mongoDatabase.listCollections()
                .filter(new Document("name", COLLECTION_NAME))
                .first();
        assertNotNull(collectionInfo, "Collection should exist");
        Document options = collectionInfo.get("options", Document.class);
        assertNotNull(options, "Collection should have options");
        assertNotNull(options.get("validator"), "Collection should have validator");
    }
}
