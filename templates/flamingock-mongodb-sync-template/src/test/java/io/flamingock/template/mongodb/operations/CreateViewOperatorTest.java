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
import io.flamingock.template.mongodb.model.operator.CreateViewOperator;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class CreateViewOperatorTest {

    private static final String DB_NAME = "test";
    private static final String SOURCE_COLLECTION = "viewSourceCollection";
    private static final String VIEW_NAME = "activeUsersView";

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
    @DisplayName("WHEN createView operator is applied THEN view is created and filters data")
    void createViewTest() {
        mongoDatabase.createCollection(SOURCE_COLLECTION);
        mongoDatabase.getCollection(SOURCE_COLLECTION).insertMany(Arrays.asList(
                new Document("name", "Active User").append("status", "active"),
                new Document("name", "Inactive User").append("status", "inactive")
        ));

        MongoOperation operation = new MongoOperation();
        operation.setType("createView");
        operation.setCollection(VIEW_NAME);

        Map<String, Object> params = new HashMap<>();
        params.put("viewOn", SOURCE_COLLECTION);

        List<Map<String, Object>> pipeline = new ArrayList<>();
        Map<String, Object> matchStage = new HashMap<>();
        Map<String, Object> matchQuery = new HashMap<>();
        matchQuery.put("status", "active");
        matchStage.put("$match", matchQuery);
        pipeline.add(matchStage);
        params.put("pipeline", pipeline);

        operation.setParameters(params);

        CreateViewOperator operator = new CreateViewOperator(mongoDatabase, operation);
        operator.apply(null);

        List<String> collections = mongoDatabase.listCollectionNames().into(new ArrayList<>());
        assertTrue(collections.contains(VIEW_NAME), "View should exist");

        List<Document> viewResults = mongoDatabase.getCollection(VIEW_NAME)
                .find()
                .into(new ArrayList<>());
        assertEquals(1, viewResults.size(), "View should return only active users");
        assertEquals("Active User", viewResults.get(0).getString("name"));
    }
}
