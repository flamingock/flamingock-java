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
import com.mongodb.client.MongoDatabase;
import io.flamingock.api.annotations.EnableFlamingock;
import io.flamingock.api.annotations.Stage;
import io.flamingock.internal.common.core.error.FlamingockException;
import io.flamingock.internal.core.runner.Runner;
import io.flamingock.mongodb.kit.MongoSyncTestKit;
import io.flamingock.community.mongodb.sync.driver.MongoSyncAuditStore;
import io.flamingock.core.kit.TestKit;
import io.flamingock.core.kit.audit.AuditTestHelper;
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

import static io.flamingock.api.StageType.LEGACY;
import static io.flamingock.api.StageType.SYSTEM;
import static io.flamingock.core.kit.audit.AuditEntryExpectation.APPLIED;
import static io.flamingock.core.kit.audit.AuditEntryExpectation.STARTED;
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
    private static MongoDatabase database;
    private MongoDbMongockTestHelper mongockTestHelper;
    private TestKit testKit;
    private AuditTestHelper auditHelper;


    @BeforeEach
    void setUp() {
        mongoClient = MongoClients.create(MongoClientSettings
                .builder()
                .applyConnectionString(new ConnectionString(mongoDBContainer.getConnectionString()))
                .build());
        database = mongoClient.getDatabase(DB_NAME);

        mongockTestHelper = new MongoDbMongockTestHelper(database.getCollection(MONGOCK_CHANGE_LOGS));
        
        // Initialize TestKit for unified testing
        testKit = MongoSyncTestKit.create(new MongoSyncAuditStore(mongoClient, "test"), mongoClient, database);
        auditHelper = testKit.getAuditHelper();

    }

    @AfterEach
    void tearDown() {
        database.drop(); // Clean between tests
        mongoClient.close();
    }

    @Test
    @Disabled("restore when https://trello.com/c/4gEQ8Wb4/458-mongock-legacy-targetsystem done")
    void testImportMongockChangeLogs() {
        //adds the Mongock
        mongockTestHelper.setupBasicScenario();

        Runner flamingock = testKit.createBuilder()
                .addDependency(mongoClient)
                .addDependency(database)
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
            STARTED("migration-from-mongock"),
            APPLIED("migration-from-mongock"),
            
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
                .addDependency(mongoClient)
                .addDependency(database)
                .build();

        //TODO should check error message, but currently it return the summary text
        Assertions.assertThrows(FlamingockException.class, flamingock::run);

    }

}