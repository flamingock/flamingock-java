/*
 * Copyright 2023 Flamingock (https://oss.flamingock.io)
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

import io.flamingock.community.Flamingock;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import io.flamingock.internal.core.community.Constants;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;

import static io.flamingock.internal.core.community.Constants.DEFAULT_AUDIT_STORE_NAME;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class MongoChangeTemplateTest {

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
        mongoDatabase.getCollection(DEFAULT_AUDIT_STORE_NAME).drop();
        mongoDatabase.getCollection(DEFAULT_AUDIT_STORE_NAME).drop();
    }



    @Test
    @DisplayName("WHEN mongodb template THEN runs fine IF Flamingock mongodb sync ce")
    void happyPath() {

        Flamingock.builder()
                .addDependency(mongoClient)
                .addDependency(mongoClient.getDatabase(DB_NAME))
                .setProperty("mongodb.databaseName", DB_NAME)
                .build()
                .run();


        List<Document> auditLog = mongoDatabase.getCollection(DEFAULT_AUDIT_STORE_NAME)
                .find()
                .into(new ArrayList<>());

        assertEquals(2, auditLog.size());
        Document createCollectionAudit = auditLog.get(0);

        assertEquals("create-users-collection-with-index", createCollectionAudit.getString("changeId"));
        assertEquals("EXECUTED", createCollectionAudit.getString("state"));
        assertEquals(MongoChangeTemplate.class.getName(), createCollectionAudit.getString(Constants.KEY_CHANGEUNIT_CLASS));

        Document seedAudit = auditLog.get(1);
        assertEquals("seed-users", seedAudit.getString("changeId"));
        assertEquals("EXECUTED", seedAudit.getString("state"));
        assertEquals(MongoChangeTemplate.class.getName(), seedAudit.getString(Constants.KEY_CHANGEUNIT_CLASS));

        List<Document> users = mongoDatabase.getCollection("users")
                .find()
                .into(new ArrayList<>());

        assertEquals(2, users.size());
        assertEquals("Admin", users.get(0).getString("name"));
        assertEquals("admin@company.com", users.get(0).getString("email"));
        assertEquals("superuser", users.get(0).getList("roles", String.class).get(0));

        assertEquals("Backup", users.get(1).getString("name"));
        assertEquals("backup@company.com", users.get(1).getString("email"));
        assertEquals("readonly", users.get(1).getList("roles", String.class).get(0));
    }


}