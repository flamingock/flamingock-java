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
import io.flamingock.template.mongodb.model.operator.RenameCollectionOperator;
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
class RenameCollectionOperatorTest {

    private static final String DB_NAME = "test";
    private static final String ORIGINAL_NAME = "originalCollection";
    private static final String RENAMED_NAME = "renamedCollection";

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
        mongoDatabase.getCollection(ORIGINAL_NAME).drop();
        mongoDatabase.getCollection(RENAMED_NAME).drop();
    }

    @Test
    @DisplayName("WHEN renameCollection operator is applied THEN collection is renamed")
    void renameCollectionTest() {
        mongoDatabase.createCollection(ORIGINAL_NAME);
        assertTrue(collectionExists(ORIGINAL_NAME), "Original collection should exist before rename");

        MongoOperation operation = new MongoOperation();
        operation.setType("renameCollection");
        operation.setCollection(ORIGINAL_NAME);
        Map<String, Object> params = new HashMap<>();
        params.put("target", RENAMED_NAME);
        operation.setParameters(params);

        RenameCollectionOperator operator = new RenameCollectionOperator(mongoDatabase, operation);
        operator.apply(null);

        assertFalse(collectionExists(ORIGINAL_NAME), "Original collection should not exist after rename");
        assertTrue(collectionExists(RENAMED_NAME), "Renamed collection should exist");
    }

    private boolean collectionExists(String collectionName) {
        List<String> collections = mongoDatabase.listCollectionNames().into(new ArrayList<>());
        return collections.contains(collectionName);
    }
}
