/*
 * Copyright 2023 Flamingock (https://www.flamingock.io)
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
package io.flamingock.importer.mongodb;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import io.flamingock.api.annotations.EnableFlamingock;
import io.flamingock.api.annotations.Stage;
import io.flamingock.internal.common.core.audit.AuditEntryField;
import io.flamingock.internal.common.core.error.FlamingockException;
import io.flamingock.internal.core.community.Constants;
import io.flamingock.internal.core.runner.Runner;
import io.flamingock.template.mongodb.MongoChangeTemplate;
import org.bson.Document;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.ArrayList;
import java.util.List;

import static io.flamingock.api.StageType.LEGACY;
import static io.flamingock.api.StageType.SYSTEM;
import static io.flamingock.internal.core.community.Constants.DEFAULT_AUDIT_STORE_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;

@EnableFlamingock(
    stages = {
        @Stage(location = "io.flamingock.importer.mongodb.system", type = SYSTEM),
        @Stage(location = "io.flamingock.importer.mongodb.legacy", type = LEGACY),
        @Stage(location = "io.flamingock.importer.mongodb.mongodb")
    }
)
@Testcontainers
public class MongoDbImporterTest {

    @Container
    public static final MongoDBContainer mongoDBContainer = new MongoDBContainer(DockerImageName.parse("mongo:6"));

    private static final String DB_NAME = "test";
    public static final String MONGOCK_CHANGE_LOGS = "mongockChangeLogs";

    private static MongoClient mongoClient;
    private static MongoDatabase mongoDatabase;
    private MongoCollection<Document> changeLogCollection;
    private MongoDbMongockTestHelper mongockTestHelper;


    @BeforeAll
    static void beforeAll() {
        mongoClient = MongoClients.create(MongoClientSettings
                .builder()
                .applyConnectionString(new ConnectionString(mongoDBContainer.getConnectionString()))
                .build());
        mongoDatabase = mongoClient.getDatabase(DB_NAME);
    }

    @BeforeEach
    void setUp() {
        System.out.println("Setting up test environment...");
        mongoDatabase.getCollection(DEFAULT_AUDIT_STORE_NAME).drop();
        mongoDatabase.getCollection(DEFAULT_AUDIT_STORE_NAME).drop();
        mongoDatabase.getCollection(MONGOCK_CHANGE_LOGS).drop();

        changeLogCollection = mongoDatabase.getCollection(MONGOCK_CHANGE_LOGS);
        mongockTestHelper = new MongoDbMongockTestHelper(changeLogCollection);
        
        // Print available templates for debugging
        System.out.println("MongoDbImporterChangeTemplate class: " + 
            io.flamingock.importer.mongodb.MongoDbImporterChangeTemplate.class.getName());
    }
    
    @Test
    void testImportMongockChangeLogs() {
        //adds the Mongock
        mongockTestHelper.setupBasicScenario();

        Runner flamingock = io.flamingock.community.Flamingock.builder()
                .addDependency(mongoClient)
                .addDependency(mongoClient.getDatabase(DB_NAME))
                .setRelaxTargetSystemValidation(true)
                .build();

        flamingock.run();

        List<Document> auditLog = mongoDatabase.getCollection(DEFAULT_AUDIT_STORE_NAME)
                .find()
                .into(new ArrayList<>());

        assertEquals(8, auditLog.size());
        Document createCollectionAudit = auditLog.get(6);

        //TODO CHECK audits from Mongock

        Assertions.assertEquals("create-users-collection-with-index", createCollectionAudit.getString("changeId"));
        Assertions.assertEquals("EXECUTED", createCollectionAudit.getString("state"));
        Assertions.assertEquals(MongoChangeTemplate.class.getName(), createCollectionAudit.getString(AuditEntryField.KEY_CHANGEUNIT_CLASS));

        Document seedAudit = auditLog.get(7);
        Assertions.assertEquals("seed-users", seedAudit.getString("changeId"));
        Assertions.assertEquals("EXECUTED", seedAudit.getString("state"));
        Assertions.assertEquals(MongoChangeTemplate.class.getName(), seedAudit.getString(AuditEntryField.KEY_CHANGEUNIT_CLASS));

        List<Document> users = mongoDatabase.getCollection("users")
                .find()
                .into(new ArrayList<>());

        assertEquals(2, users.size());
        Assertions.assertEquals("Admin", users.get(0).getString("name"));
        Assertions.assertEquals("admin@company.com", users.get(0).getString("email"));
        Assertions.assertEquals("superuser", users.get(0).getList("roles", String.class).get(0));

        Assertions.assertEquals("Backup", users.get(1).getString("name"));
        Assertions.assertEquals("backup@company.com", users.get(1).getString("email"));
        Assertions.assertEquals("readonly", users.get(1).getList("roles", String.class).get(0));
    }


    @Test
    void failIfEmptyOrigin() {
        //adds the Mongock

        Runner flamingock = io.flamingock.community.Flamingock.builder()
                .addDependency(mongoClient)
                .addDependency(mongoClient.getDatabase(DB_NAME))
                .setRelaxTargetSystemValidation(true)
                .build();

        //TODO should check error message, but currently it return the summary text
        Assertions.assertThrows(FlamingockException.class, flamingock::run);

    }

}