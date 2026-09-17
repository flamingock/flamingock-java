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
package io.flamingock.store.mongodb.reactive;

import com.mongodb.reactivestreams.client.ClientSession;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.mongodb.reactivestreams.client.MongoDatabase;
import io.flamingock.common.test.pipeline.CodeChangeTestDefinition;
import io.flamingock.core.kit.TestKit;
import io.flamingock.core.kit.audit.AuditEntryExpectation;
import io.flamingock.core.kit.audit.AuditTestSupport;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.feature.Features;
import io.flamingock.internal.common.core.journal.JournalEvent;
import io.flamingock.internal.common.mongodb.MongoDBJournalEventMapper;
import io.flamingock.internal.util.FeatureFlag;
import io.flamingock.mongodb.reactive.kit.MongoDBReactiveTestKit;
import io.flamingock.store.mongodb.reactive.changes._001__create_client_collection_happy;
import io.flamingock.store.mongodb.reactive.changes._002__insert_federico_happy_transactional;
import io.flamingock.targetsystem.mongodb.reactive.MongoDBReactiveTargetSystem;
import io.flamingock.reactive.util.PublisherSync;
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
 * End-to-end coverage for the journal feature gate through a complete native reactive runner execution.
 */
@Testcontainers
class MongoDBReactiveJournalFeatureFlagE2ETest {

    private static final String DB_NAME = "test";
    private static final String JOURNAL_COLLECTION = "flamingockJournalEvents";
    private static final String DEFAULT_STAGE_NAME = "default-stage-name";

    @Container
    static final MongoDBContainer mongoDBContainer =
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
        MongoDBReactiveTargetSystem targetSystem = new MongoDBReactiveTargetSystem("mongodb", mongoClient, DB_NAME);
        testKit = MongoDBReactiveTestKit.create(
                MongoDBReactiveAuditStore.from(targetSystem), mongoClient, database);
        mongoDBTestHelper = new MongoDBTestHelper(database);
    }

    @AfterEach
    void tearDown() {
        FeatureFlag.remove(Features.JOURNAL_EVENTS);
        testKit.cleanUp();
        mongoClient.close();
    }

    @Test
    @DisplayName("journal disabled keeps the audit history and creates no journal collection")
    void journalDisabledLeavesNoJournalCollection() {
        runPipeline(
                STARTED("create-client-collection"),
                APPLIED("create-client-collection"),
                STARTED("insert-federico-document"),
                APPLIED("insert-federico-document"));

        assertFalse(mongoDBTestHelper.collectionExists(JOURNAL_COLLECTION));
    }

    @Test
    @DisplayName("journal enabled keeps current audit state and the complete event history")
    void journalEnabledSplitsCurrentStateFromHistory() {
        FeatureFlag.enable(Features.JOURNAL_EVENTS);

        runPipeline(
                APPLIED("create-client-collection"),
                APPLIED("insert-federico-document"));

        assertTrue(mongoDBTestHelper.collectionExists(JOURNAL_COLLECTION));
        List<JournalEvent<AuditEntry>> events = storedEvents();
        assertEquals(4, events.size());
        assertTrue(events.stream().allMatch(event -> DEFAULT_STAGE_NAME.equals(event.getStreamId())));
        assertEquals(Arrays.asList(1L, 2L, 3L, 4L), events.stream()
                .map(JournalEvent::getStreamSequence)
                .sorted()
                .collect(Collectors.toList()));
        assertEquals(Arrays.asList(AuditEntry.Status.STARTED, AuditEntry.Status.STARTED,
                        AuditEntry.Status.APPLIED, AuditEntry.Status.APPLIED),
                events.stream().map(event -> event.getData().getState()).sorted().collect(Collectors.toList()));
    }

    private void runPipeline(AuditEntryExpectation... expectedAudits) {
        MongoDBReactiveTargetSystem targetSystem = new MongoDBReactiveTargetSystem("mongodb", mongoClient, DB_NAME);
        AuditTestSupport.withTestKit(testKit)
                .GIVEN_Changes(
                        new CodeChangeTestDefinition(_001__create_client_collection_happy.class,
                                Collections.singletonList(MongoDatabase.class)),
                        new CodeChangeTestDefinition(_002__insert_federico_happy_transactional.class,
                                Arrays.asList(MongoDatabase.class, ClientSession.class)))
                .WHEN(() -> testKit.createBuilder()
                        .setAuditStore(MongoDBReactiveAuditStore.from(targetSystem))
                        .addTargetSystem(targetSystem)
                        .build()
                        .run())
                .THEN_VerifyAuditSequenceStrict(expectedAudits)
                .run();
    }

    private List<JournalEvent<AuditEntry>> storedEvents() {
        if (!mongoDBTestHelper.collectionExists(JOURNAL_COLLECTION)) {
            return new ArrayList<>();
        }
        return PublisherSync.collect(database.getCollection(JOURNAL_COLLECTION).find())
                .stream()
                .map(mapper::fromDocument)
                .collect(Collectors.toList());
    }
}
