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
package io.flamingock.importer.mongock.mongodb;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import io.flamingock.api.annotations.EnableFlamingock;
import io.flamingock.api.annotations.Stage;
import io.flamingock.community.mongodb.sync.driver.MongoDBSyncAuditStore;
import io.flamingock.core.kit.TestKit;
import io.flamingock.core.kit.audit.AuditTestHelper;
import io.flamingock.internal.common.core.error.FlamingockException;
import io.flamingock.internal.core.runner.Runner;
import io.flamingock.mongodb.kit.MongoDBSyncTestKit;
import io.flamingock.support.mongock.annotations.MongockSupport;
import io.flamingock.targetystem.mongodb.sync.MongoDBSyncTargetSystem;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.ArrayList;
import java.util.List;

import static io.flamingock.core.kit.audit.AuditEntryExpectation.APPLIED;
import static io.flamingock.core.kit.audit.AuditEntryExpectation.STARTED;
import static io.flamingock.internal.common.core.metadata.Constants.DEFAULT_MONGOCK_ORIGIN;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Testcontainers
@MongockSupport(targetSystem = "mongodb-target-system")
@EnableFlamingock(stages = {@Stage(location = "io.flamingock.importer.mongock.mongodb.changes")})
public class MongoDBImporterTest {

    @Container
    private static final MongoDBContainer mongoDBContainer = new MongoDBContainer(DockerImageName.parse("mongo:6"));
    private static final String DB_NAME = "test";
    private static final String DATABASE_NAME = "test";
    private static MongoClient mongoClient;
    private static MongoDatabase database;
    private MongoDBMongockTestHelper mongockTestHelper;
    private TestKit testKit;
    private AuditTestHelper auditHelper;


    @BeforeEach
    void setUp() {
        mongoClient = MongoClients.create(MongoClientSettings
                .builder()
                .applyConnectionString(new ConnectionString(mongoDBContainer.getConnectionString()))
                .build());
        database = mongoClient.getDatabase(DB_NAME);

        mongockTestHelper = new MongoDBMongockTestHelper(database.getCollection(DEFAULT_MONGOCK_ORIGIN));

        // Initialize TestKit for unified testing
        testKit = MongoDBSyncTestKit.create(new MongoDBSyncAuditStore(mongoClient, DATABASE_NAME), mongoClient, database);
        auditHelper = testKit.getAuditHelper();

    }

    @AfterEach
    void tearDown() {
        database.drop(); // Clean between tests
        mongoClient.close();
    }

    @Test
    void testImportMongockChangeLogs() {
        //adds the Mongock
        mongockTestHelper.setupBasicScenario();


        MongoDBSyncTargetSystem mongodbTargetSystem = new MongoDBSyncTargetSystem("mongodb-target-system", mongoClient, DATABASE_NAME);

        Runner flamingock = testKit.createBuilder()
                .addTargetSystem(mongodbTargetSystem)
                .build();

        flamingock.run();

        // Verify audit sequence: 11 total entries as shown in actual execution
        // Legacy imports only show APPLIED (imported from Mongock), new changes show STARTED+APPLIED
        auditHelper.verifyAuditSequenceStrict(
                // Legacy imports from Mongock (APPLIED only - no STARTED for imported changes)
                APPLIED("system-change-00001_before"),
                APPLIED("system-change-00001"),
                APPLIED("client-initializer_before"),
                APPLIED("client-initializer"),
                APPLIED("client-updater"),

                // System stage - actual system importer change
                STARTED("migration-mongock-to-flamingock-community"),
                APPLIED("migration-mongock-to-flamingock-community"),

                // Application stage - new changes created by templates
                STARTED("create-users-collection-with-index"),
                APPLIED("create-users-collection-with-index"),
                STARTED("seed-users"),
                APPLIED("seed-users")
        );


        // Validate actual change
        List<Document> users = database.getCollection("users")
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
    @Disabled("restore when https://trello.com/c/4gEQ8Wb4/458-mongock-legacy-targetsystem done")
    void failIfEmptyOrigin() {
        //adds the Mongock

        Runner flamingock = testKit.createBuilder()
                .build();

        //TODO should check error message, but currently it return the summary text
        Assertions.assertThrows(FlamingockException.class, flamingock::run);

    }

}
