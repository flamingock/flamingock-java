/*
 * Copyright 2026 Flamingock (https://www.flamingock.io)
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
package io.flamingock.store.mongodb.sync;

import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import io.flamingock.common.test.pipeline.CodeChangeTestDefinition;
import io.flamingock.core.kit.TestKit;
import io.flamingock.core.kit.audit.AuditTestSupport;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.feature.Features;
import io.flamingock.internal.common.core.journal.JournalEvent;
import io.flamingock.internal.common.mongodb.MongoDBJournalEventMapper;
import io.flamingock.internal.util.FeatureFlag;
import io.flamingock.mongodb.kit.MongoDBSyncTestKit;
import io.flamingock.store.mongodb.sync.changes._001__create_client_collection_happy;
import io.flamingock.store.mongodb.sync.changes._002__insert_federico_happy_transactional;
import io.flamingock.targetsystem.mongodb.sync.MongoDBSyncTargetSystem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static io.flamingock.core.kit.audit.AuditEntryExpectation.APPLIED;
import static io.flamingock.core.kit.audit.AuditEntryExpectation.STARTED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end counterpart to {@code MongoDBSyncAuditPersistenceJournalTest}: proves the
 * {@code Features.JOURNAL_EVENTS} gate holds through a full runner pass, which is how production actually
 * reaches the journal.
 */
@Testcontainers
class MongoDBSyncJournalFeatureFlagE2ETest {

    private static final String DB_NAME = "test";
    private static final String JOURNAL_COLLECTION = "flamingockJournalEvents";
    /** The stage name {@code PipelineTestHelper} assigns, which becomes the journal stream. */
    private static final String DEFAULT_STAGE_NAME = "default-stage-name";

    @Container
    public static final MongoDBContainer mongoDBContainer =
            new MongoDBContainer(DockerImageName.parse("mongo:6")).withReuse(true);

    private final MongoDBJournalEventMapper mapper = new MongoDBJournalEventMapper();

    private MongoClient mongoClient;
    private MongoDatabase database;
    private TestKit testKit;
    private MongoDBTestHelper mongoDBTestHelper;

    @BeforeEach
    void setUp() {
        mongoClient = MongoClients.create(mongoDBContainer.getConnectionString());
        database = mongoClient.getDatabase(DB_NAME);
        testKit = MongoDBSyncTestKit.create(
                MongoDBSyncAuditStore.from(new MongoDBSyncTargetSystem("mongodb", mongoClient, DB_NAME)),
                mongoClient, database);
        mongoDBTestHelper = new MongoDBTestHelper(database);
    }

    @AfterEach
    void tearDown() {
        // The flag is process-global and every test class in this module shares one JVM, so leaving it on
        // would silently make later classes create and write the journal collection.
        FeatureFlag.remove(Features.JOURNAL_EVENTS);
        database.drop();
        mongoClient.close();
    }

    @Test
    @DisplayName("journal disabled: a full run writes its audit log and never creates the journal collection")
    void journalDisabledLeavesNoJournalCollection() {
        runPipeline();

        assertFalse(mongoDBTestHelper.collectionExists(JOURNAL_COLLECTION),
                "the journal collection must not exist when the feature is disabled");
    }

    @Test
    @DisplayName("journal enabled: a full run mirrors every audit entry as an ordered event on the stage stream")
    void journalEnabledMirrorsEveryAuditEntry() {
        FeatureFlag.enable(Features.JOURNAL_EVENTS);

        runPipeline();

        assertTrue(mongoDBTestHelper.collectionExists(JOURNAL_COLLECTION));
        List<JournalEvent<AuditEntry>> events = storedEvents();

        // Two changes, each audited as STARTED then APPLIED.
        assertEquals(4, events.size(), "one event per audit write");
        assertTrue(events.stream().allMatch(event -> DEFAULT_STAGE_NAME.equals(event.getStreamId())),
                "the stream is the stage");
        assertEquals(Arrays.asList(1L, 2L, 3L, 4L),
                events.stream().map(JournalEvent::getStreamSequence).sorted().collect(Collectors.toList()),
                "sequences are contiguous from 1");
        assertEquals(Arrays.asList("create-client-collection", "create-client-collection",
                        "insert-federico-document", "insert-federico-document"),
                events.stream().map(event -> event.getData().getChangeId()).sorted().collect(Collectors.toList()),
                "each event carries the audit entry it mirrors");
    }

    // ----------------------------- helpers -----------------------------

    private void runPipeline() {
        MongoDBSyncTargetSystem targetSystem = new MongoDBSyncTargetSystem("mongodb", mongoClient, DB_NAME);
        AuditTestSupport.withTestKit(testKit)
                .GIVEN_Changes(
                        new CodeChangeTestDefinition(_001__create_client_collection_happy.class,
                                Collections.singletonList(MongoDatabase.class)),
                        new CodeChangeTestDefinition(_002__insert_federico_happy_transactional.class,
                                Arrays.asList(MongoDatabase.class, ClientSession.class)))
                .WHEN(() -> testKit.createBuilder()
                        .setAuditStore(MongoDBSyncAuditStore.from(targetSystem))
                        .addTargetSystem(targetSystem)
                        .build()
                        .run())
                .THEN_VerifyAuditSequenceStrict(
                        STARTED("create-client-collection"),
                        APPLIED("create-client-collection"),
                        STARTED("insert-federico-document"),
                        APPLIED("insert-federico-document"))
                .run();
    }

    private List<JournalEvent<AuditEntry>> storedEvents() {
        List<JournalEvent<AuditEntry>> events = new ArrayList<>();
        database.getCollection(JOURNAL_COLLECTION).find().forEach(document -> events.add(mapper.fromDocument(document)));
        return events;
    }
}
