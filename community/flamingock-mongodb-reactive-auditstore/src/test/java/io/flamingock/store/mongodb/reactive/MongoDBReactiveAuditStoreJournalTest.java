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

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.reactivestreams.client.MongoDatabase;
import io.flamingock.core.kit.audit.AuditEntryTestFactory;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.audit.AuditPersistenceFactory;
import io.flamingock.internal.common.core.audit.AuditTxType;
import io.flamingock.internal.common.core.error.FlamingockException;
import io.flamingock.internal.common.core.feature.Features;
import io.flamingock.internal.common.core.journal.JournalEvent;
import io.flamingock.internal.common.mongodb.MongoDBJournalEventMapper;
import io.flamingock.internal.core.configuration.community.CommunityConfiguration;
import io.flamingock.internal.core.external.store.audit.community.CommunityAuditPersistence;
import io.flamingock.internal.core.context.SimpleContext;
import io.flamingock.internal.util.FeatureFlag;
import io.flamingock.internal.util.id.RunnerId;
import io.flamingock.reactive.util.PublisherSync;
import io.flamingock.targetsystem.mongodb.reactive.MongoDBReactiveTargetSystem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.bson.Document;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class MongoDBReactiveAuditStoreJournalTest {

    private static final String DB_NAME = "test";
    private static final String AUDIT_COLLECTION = "storeAudit";
    private static final String LOCK_COLLECTION = "storeLock";
    private static final String JOURNAL_COLLECTION = "storeJournal";

    @Container
    static final MongoDBContainer mongoDBContainer =
            new MongoDBContainer(DockerImageName.parse("mongo:6")).withReuse(true);

    private final MongoDBJournalEventMapper mapper = new MongoDBJournalEventMapper();

    private MongoClient mongoClient;
    private MongoDatabase database;
    private SimpleContext context;

    @BeforeEach
    void setUp() {
        mongoClient = MongoClients.create(mongoDBContainer.getConnectionString());
        database = mongoClient.getDatabase(DB_NAME);
        context = new SimpleContext();
        context.addDependency(RunnerId.generate());
        context.addDependency(new CommunityConfiguration());
    }

    @AfterEach
    void tearDown() {
        FeatureFlag.remove(Features.JOURNAL_EVENTS);
        PublisherSync.complete(database.drop());
        mongoClient.close();
    }

    @Test
    @DisplayName("the flag-off store keeps historical audit rows and does not create the journal")
    void flagOffKeepsHistoricalAuditRows() {
        MongoDBReactiveAuditStore auditStore = initializeStore();
        CommunityAuditPersistence persistence = auditStore.getPersistenceFactory().get("stage-one");

        persistence.writeEntry(auditEntry("change-1", AuditEntry.Status.STARTED));
        persistence.writeEntry(auditEntry("change-1", AuditEntry.Status.APPLIED));

        assertEquals(2, persistence.getAuditHistory().size());
        assertFalse(collectionExists(JOURNAL_COLLECTION));
    }

    @Test
    @DisplayName("journal-enabled store creates independent streams for each stage")
    void flagOnCreatesStageAwareJournalStreams() {
        FeatureFlag.enable(Features.JOURNAL_EVENTS);
        MongoDBReactiveAuditStore auditStore = initializeStore();
        AuditPersistenceFactory<CommunityAuditPersistence> factory = auditStore.getPersistenceFactory();

        factory.get("stage-one").writeEntry(auditEntry("change-one", AuditEntry.Status.APPLIED));
        factory.get("stage-two").writeEntry(auditEntry("change-two", AuditEntry.Status.APPLIED));

        List<JournalEvent<AuditEntry>> events = storedEvents();
        assertEquals(2, events.size());
        assertTrue(events.stream().anyMatch(event -> "stage-one".equals(event.getStreamId())
                && event.getStreamSequence() == 1L));
        assertTrue(events.stream().anyMatch(event -> "stage-two".equals(event.getStreamId())
                && event.getStreamSequence() == 1L));
    }

    @Test
    @DisplayName("recreated stage persistence resumes its stream without affecting other stages")
    void recreatedStagePersistenceReseedsOnlyItsOwnStream() {
		FeatureFlag.enable(Features.JOURNAL_EVENTS);
		AuditPersistenceFactory<CommunityAuditPersistence> firstFactory = initializeStore().getPersistenceFactory();
		firstFactory.get("stage-one").writeEntry(auditEntry("stage-one-first", AuditEntry.Status.APPLIED));
		firstFactory.get("stage-two").writeEntry(auditEntry("stage-two-first", AuditEntry.Status.APPLIED));

		initializeStore().getPersistenceFactory()
				.get("stage-one")
				.writeEntry(auditEntry("stage-one-second", AuditEntry.Status.APPLIED));

		Map<String, List<JournalEvent<AuditEntry>>> eventsByStream = storedEvents().stream()
				.collect(Collectors.groupingBy(JournalEvent::getStreamId));

		assertEquals(Arrays.asList(1L, 2L), sequences(eventsByStream.get("stage-one")));
		assertEquals(Arrays.asList(1L), sequences(eventsByStream.get("stage-two")));
    }

    @Test
    @DisplayName("an audit-only installation remains readable until journal persistence is first accessed")
    void auditOnlyUpgradeCreatesJournalSchemaLazily() {
		MongoDBReactiveAuditStore legacyStore = initializeStore();
		CommunityAuditPersistence legacyPersistence = legacyStore.getPersistenceFactory().get("legacy-stage");
		legacyPersistence.writeEntry(auditEntry("legacy-started", AuditEntry.Status.STARTED));
		legacyPersistence.writeEntry(auditEntry("legacy-applied", AuditEntry.Status.APPLIED));

		assertFalse(collectionExists(JOURNAL_COLLECTION));
		assertEquals(Arrays.asList("legacy-applied", "legacy-started"), auditChangeIds(legacyPersistence.getAuditHistory()));

		FeatureFlag.enable(Features.JOURNAL_EVENTS);
		MongoDBReactiveAuditStore upgradedStore = initializeStore();
		assertEquals(Arrays.asList("legacy-applied", "legacy-started"), auditChangeIds(upgradedStore.getAuditReader().getAuditHistory()));
		assertFalse(collectionExists(JOURNAL_COLLECTION));

		upgradedStore.getPersistenceFactory().get("upgrade-stage");

		Map<String, Document> indexes = indexesByName(JOURNAL_COLLECTION);
		assertTrue(collectionExists(JOURNAL_COLLECTION));
		assertTrue(indexes.get("unique_key_sequence").getBoolean("unique"));
		assertFalse(indexes.get("unacknowledged_by_key_sequence").getBoolean("unique", false));
		assertEquals(new Document("acknowledged", false), indexes.get("unacknowledged_by_key_sequence")
				.get("partialFilterExpression", Document.class));
		assertTrue(indexes.get("unique_event_id").getBoolean("unique"));
		assertEquals(0, storedEvents().size());
	}

	@Test
	@DisplayName("manual schemas validate at their lazy access boundaries")
	void manualSchemaValidationIsLazyAndAcceptsValidSchema() {
		createManualSchema(false);
		MongoDBReactiveAuditStore validStore = initializeManualStore();
		validStore.getAuditReader().getAuditHistory();
		validStore.getPersistenceFactory().get("manual-stage");

		PublisherSync.complete(database.drop());
		assertInvalidSchema(LOCK_COLLECTION, () -> initializeManualStore());

		PublisherSync.complete(database.drop());
		createLockSchema();
		MongoDBReactiveAuditStore missingAuditStore = initializeManualStore();
		assertInvalidSchema(AUDIT_COLLECTION, () -> missingAuditStore.getAuditReader());
		assertInvalidSchema(AUDIT_COLLECTION, () -> missingAuditStore.getPersistenceFactory().get("manual-stage"));

		PublisherSync.complete(database.drop());
		createLockSchema();
		createAuditSchema();
		MongoDBReactiveAuditStore missingJournalStore = initializeManualStore();
		missingJournalStore.getAuditReader().getAuditHistory();
		assertInvalidSchema(JOURNAL_COLLECTION, () -> missingJournalStore.getPersistenceFactory().get("manual-stage"));
	}

	@Test
	@DisplayName("manual journal validation rejects each required-index contract boundary")
	void manualJournalValidationRejectsInvalidRequiredIndexes() {
		assertInvalidJournalIndex(() -> { });
		assertInvalidJournalIndex(() -> createIndex(JOURNAL_COLLECTION, new Document("streamId", 1).append("streamSequence", 1),
				new IndexOptions().name("unique_key_sequence").unique(false)));
		assertInvalidJournalIndex(() -> createIndex(JOURNAL_COLLECTION,
				new Document("acknowledged", 1).append("streamId", 1).append("streamSequence", 1),
				new IndexOptions().name("unacknowledged_by_key_sequence").partialFilterExpression(new Document("acknowledged", true))));
		assertInvalidJournalIndex(() -> createIndex(JOURNAL_COLLECTION, new Document("wrong", 1),
				new IndexOptions().name("unique_event_id").unique(true)));
	}

	@Test
	@DisplayName("manual journal validation accepts descending keys and extra non-unique indexes")
	void manualJournalValidationAcceptsToleratedIndexDifferences() {
		createManualSchema(true);
		MongoDBReactiveAuditStore descendingStore = initializeManualStore();
		descendingStore.getPersistenceFactory().get("descending-stage");

		PublisherSync.complete(database.drop());
		createManualSchema(false);
		createIndex(JOURNAL_COLLECTION, new Document("diagnostic", 1), new IndexOptions().name("diagnostic_index"));
		MongoDBReactiveAuditStore extraIndexStore = initializeManualStore();
		extraIndexStore.getPersistenceFactory().get("extra-index-stage");
	}

	@Test
	@DisplayName("stage persistence and audit reader coexist without duplicate journal events")
	void stagePersistenceAndAuditReaderCoexist() {
		FeatureFlag.enable(Features.JOURNAL_EVENTS);
		MongoDBReactiveAuditStore auditStore = initializeStore();
		CommunityAuditPersistence persistence = auditStore.getPersistenceFactory().get("coexist-stage");
		persistence.writeEntry(auditEntry("coexist-change", AuditEntry.Status.APPLIED));

		assertEquals(Arrays.asList("coexist-change"), auditChangeIds(auditStore.getAuditReader().getAuditHistory()));
		assertEquals(1, storedEvents().size());
		assertEquals("coexist-stage", storedEvents().get(0).getStreamId());
		assertEquals(1L, storedEvents().get(0).getStreamSequence());
	}

	@Test
	@DisplayName("journal repository name is required and must be different from audit and lock names")
	void journalRepositoryNameMustBeUnique() {
		MongoDBReactiveTargetSystem targetSystem = initializedTargetSystem();
		MongoDBReactiveAuditStore sameAsAudit = MongoDBReactiveAuditStore.from(targetSystem)
				.withAuditRepositoryName(AUDIT_COLLECTION)
				.withLockRepositoryName(LOCK_COLLECTION)
				.withJournalRepositoryName(AUDIT_COLLECTION);

		assertThrows(FlamingockException.class, () -> sameAsAudit.initialize(context));

		MongoDBReactiveAuditStore blank = MongoDBReactiveAuditStore.from(targetSystem)
				.withAuditRepositoryName(AUDIT_COLLECTION)
				.withLockRepositoryName(LOCK_COLLECTION)
				.withJournalRepositoryName(" ");
		MongoDBReactiveAuditStore nullName = MongoDBReactiveAuditStore.from(targetSystem)
				.withAuditRepositoryName(AUDIT_COLLECTION)
				.withLockRepositoryName(LOCK_COLLECTION)
				.withJournalRepositoryName(null);
		MongoDBReactiveAuditStore normalizedAuditCollision = MongoDBReactiveAuditStore.from(targetSystem)
				.withAuditRepositoryName(AUDIT_COLLECTION)
				.withLockRepositoryName(LOCK_COLLECTION)
				.withJournalRepositoryName("  STOREAUDIT  ");
		MongoDBReactiveAuditStore normalizedLockCollision = MongoDBReactiveAuditStore.from(targetSystem)
				.withAuditRepositoryName(AUDIT_COLLECTION)
				.withLockRepositoryName(LOCK_COLLECTION)
				.withJournalRepositoryName("  STORELOCK  ");

		assertThrows(FlamingockException.class, () -> blank.initialize(context));
		assertThrows(FlamingockException.class, () -> nullName.initialize(context));
		assertThrows(FlamingockException.class, () -> normalizedAuditCollision.initialize(context));
		assertThrows(FlamingockException.class, () -> normalizedLockCollision.initialize(context));
	}

	private MongoDBReactiveAuditStore initializeManualStore() {
		FeatureFlag.enable(Features.JOURNAL_EVENTS);
		MongoDBReactiveAuditStore auditStore = MongoDBReactiveAuditStore.from(initializedTargetSystem())
				.withAuditRepositoryName(AUDIT_COLLECTION)
				.withLockRepositoryName(LOCK_COLLECTION)
				.withJournalRepositoryName(JOURNAL_COLLECTION)
				.withAutoCreate(false);
		auditStore.initialize(context);
		return auditStore;
	}

	private void assertInvalidJournalIndex(Executable mutation) {
		PublisherSync.complete(database.drop());
		createLockSchema();
		createAuditSchema();
		createCollection(JOURNAL_COLLECTION);
		try {
			mutation.execute();
		} catch (Throwable throwable) {
			throw new RuntimeException(throwable);
		}
		assertInvalidSchema(JOURNAL_COLLECTION, () -> initializeManualStore().getPersistenceFactory().get("invalid-stage"));
	}

	private void assertInvalidSchema(String collectionName, Executable access) {
		RuntimeException exception = assertThrows(RuntimeException.class, access);
		assertTrue(exception.getMessage().contains(collectionName));
	}

	private void createManualSchema(boolean descending) {
		createLockSchema();
		createAuditSchema();
		createJournalSchema(descending);
	}

	private void createLockSchema() {
		createCollection(LOCK_COLLECTION);
		createIndex(LOCK_COLLECTION, new Document("key", 1), new IndexOptions().unique(true));
	}

	private void createAuditSchema() {
		createCollection(AUDIT_COLLECTION);
		createIndex(AUDIT_COLLECTION, new Document("executionId", 1).append("changeId", 1).append("state", 1),
				new IndexOptions().unique(true));
	}

	private void createJournalSchema(boolean descending) {
		createCollection(JOURNAL_COLLECTION);
		int direction = descending ? -1 : 1;
		createIndex(JOURNAL_COLLECTION, new Document("streamId", direction).append("streamSequence", direction),
				new IndexOptions().name("unique_key_sequence").unique(true));
		createIndex(JOURNAL_COLLECTION, new Document("acknowledged", direction).append("streamId", direction)
						.append("streamSequence", direction),
				new IndexOptions().name("unacknowledged_by_key_sequence")
						.partialFilterExpression(new Document("acknowledged", false)));
		createIndex(JOURNAL_COLLECTION, new Document("eventId", direction),
				new IndexOptions().name("unique_event_id").unique(true));
	}

	private void createCollection(String collectionName) {
		PublisherSync.complete(database.createCollection(collectionName));
	}

	private void createIndex(String collectionName, Document keys, IndexOptions options) {
		PublisherSync.first(database.getCollection(collectionName).createIndex(keys, options));
	}

	private Map<String, Document> indexesByName(String collectionName) {
		return PublisherSync.collect(database.getCollection(collectionName).listIndexes()).stream()
				.collect(Collectors.toMap(index -> index.getString("name"), index -> index));
	}

	private static List<String> auditChangeIds(List<AuditEntry> entries) {
		return entries.stream().map(AuditEntry::getChangeId).sorted().collect(Collectors.toList());
    }

    private MongoDBReactiveAuditStore initializeStore() {
        MongoDBReactiveTargetSystem targetSystem = initializedTargetSystem();
        MongoDBReactiveAuditStore auditStore = MongoDBReactiveAuditStore.from(targetSystem)
                .withAuditRepositoryName(AUDIT_COLLECTION)
                .withLockRepositoryName(LOCK_COLLECTION)
                .withJournalRepositoryName(JOURNAL_COLLECTION);
        auditStore.initialize(context);
        return auditStore;
    }

    private MongoDBReactiveTargetSystem initializedTargetSystem() {
        MongoDBReactiveTargetSystem targetSystem = new MongoDBReactiveTargetSystem("mongodb", mongoClient, DB_NAME);
        targetSystem.initialize(context);
        return targetSystem;
    }

    private List<JournalEvent<AuditEntry>> storedEvents() {
        if (!collectionExists(JOURNAL_COLLECTION)) {
            return new ArrayList<>();
        }
        return PublisherSync.collect(database.getCollection(JOURNAL_COLLECTION).find())
                .stream()
                .map(mapper::fromDocument)
                .collect(Collectors.toList());
    }

    private boolean collectionExists(String collectionName) {
        return PublisherSync.collect(database.listCollectionNames()).contains(collectionName);
    }

    private static List<Long> sequences(List<JournalEvent<AuditEntry>> events) {
		return events.stream()
				.map(JournalEvent::getStreamSequence)
				.sorted()
				.collect(Collectors.toList());
    }

    private static AuditEntry auditEntry(String changeId, AuditEntry.Status status) {
        return AuditEntryTestFactory.createTestAuditEntry(changeId, status, AuditTxType.NON_TX, (Class<?>) null);
    }
}
