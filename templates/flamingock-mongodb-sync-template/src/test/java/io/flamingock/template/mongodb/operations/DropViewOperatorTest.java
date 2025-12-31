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
import io.flamingock.template.mongodb.model.operator.DropViewOperator;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class DropViewOperatorTest {

    private static final String DB_NAME = "test";
    private static final String SOURCE_COLLECTION = "dropViewSourceCollection";
    private static final String VIEW_NAME = "viewToDrop";

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
        mongoDatabase.getCollection(SOURCE_COLLECTION).drop();
        mongoDatabase.getCollection(VIEW_NAME).drop();
    }

    @Test
    @DisplayName("WHEN dropView operator is applied THEN view is dropped")
    void dropViewTest() {
        mongoDatabase.createCollection(SOURCE_COLLECTION);
        mongoDatabase.createView(VIEW_NAME, SOURCE_COLLECTION, Collections.emptyList());
        assertTrue(collectionExists(VIEW_NAME), "View should exist before drop");

        MongoOperation operation = new MongoOperation();
        operation.setType("dropView");
        operation.setCollection(VIEW_NAME);
        operation.setParameters(new HashMap<>());

        DropViewOperator operator = new DropViewOperator(mongoDatabase, operation);
        operator.apply(null);

        assertFalse(collectionExists(VIEW_NAME), "View should have been dropped");
    }

    private boolean collectionExists(String collectionName) {
        List<String> collections = mongoDatabase.listCollectionNames().into(new ArrayList<>());
        return collections.contains(collectionName);
    }
}
