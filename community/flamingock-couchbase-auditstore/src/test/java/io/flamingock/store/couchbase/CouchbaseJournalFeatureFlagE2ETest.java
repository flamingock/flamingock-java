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
package io.flamingock.store.couchbase;

import com.couchbase.client.core.io.CollectionIdentifier;
import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.ClusterOptions;
import com.couchbase.client.java.Collection;
import io.flamingock.common.test.pipeline.CodeChangeTestDefinition;
import io.flamingock.core.kit.audit.AuditEntryExpectation;
import io.flamingock.core.kit.audit.AuditTestSupport;
import io.flamingock.couchbase.kit.CouchbaseTestKit;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.feature.Features;
import io.flamingock.internal.common.core.journal.JournalEvent;
import io.flamingock.internal.common.couchbase.CouchbaseCollectionHelper;
import io.flamingock.internal.common.couchbase.CouchbaseJournalEventMapper;
import io.flamingock.internal.util.FeatureFlag;
import io.flamingock.internal.util.constants.CommunityPersistenceConstants;
import io.flamingock.store.couchbase.changes.happyPath._002__insert_document;
import io.flamingock.targetsystem.couchbase.CouchbaseTargetSystem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.couchbase.BucketDefinition;
import org.testcontainers.couchbase.CouchbaseContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
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
 * End-to-end coverage for the {@link Features#JOURNAL_EVENTS} gate through a complete runner execution.
 */
@Testcontainers
class CouchbaseJournalFeatureFlagE2ETest {

    private static final String BUCKET_NAME = "test";
    private static final String SCOPE_NAME = CollectionIdentifier.DEFAULT_SCOPE;
    private static final String JOURNAL_COLLECTION = "flamingockJournalEvents";
    private static final String DEFAULT_STAGE_NAME = "default-stage-name";

    @Container
    static final CouchbaseContainer couchbaseContainer = new CouchbaseContainer("couchbase/server:7.2.4")
            .withBucket(new BucketDefinition(BUCKET_NAME));

    private static Cluster cluster;

    private final CouchbaseJournalEventMapper mapper = new CouchbaseJournalEventMapper();

    private CouchbaseTargetSystem targetSystem;
    private CouchbaseAuditStore auditStore;
    private CouchbaseTestKit testKit;

    @BeforeAll
    static void beforeAll() {
        couchbaseContainer.start();
        // Default KV timeout (2.5s) is too tight for a just-created collection: Couchbase's KV service can
        // take a few seconds to pick up a brand-new collection's manifest entry, and the very first KV op
        // against it (here, the test kit's own audit-history check) can hit that gap and time out with
        // UnambiguousTimeoutException/KV_COLLECTION_OUTDATED — nothing to do with the journal logic under
        // test. Widening it gives the SDK's own retry loop room to ride out that window.
        cluster = Cluster.connect(
                couchbaseContainer.getConnectionString(),
                ClusterOptions.clusterOptions(couchbaseContainer.getUsername(), couchbaseContainer.getPassword())
                        .environment(env -> env.timeoutConfig(timeouts -> timeouts.kvTimeout(Duration.ofSeconds(10)))));
        cluster.bucket(BUCKET_NAME).waitUntilReady(Duration.ofSeconds(10));
    }

    @BeforeEach
    void setUp() {
        targetSystem = new CouchbaseTargetSystem("couchbase", cluster, BUCKET_NAME);
        auditStore = CouchbaseAuditStore.from(targetSystem);
        testKit = CouchbaseTestKit.create(auditStore, cluster, BUCKET_NAME, SCOPE_NAME);
    }

    @AfterEach
    void tearDown() {
        // The flag is process-global and every test class in this module shares one JVM, so leaving it on
        // would silently make later classes create and write the journal collection.
        FeatureFlag.remove(Features.JOURNAL_EVENTS);
        testKit.cleanUp();
    }

    @Test
    @DisplayName("journal disabled: the audit log retains every state transition")
    void journalDisabledRetainsHistoricalAuditEntries() {
        runPipeline(STARTED("insert-document"), APPLIED("insert-document"));

        assertFalse(CouchbaseCollectionHelper.collectionExists(cluster, BUCKET_NAME, SCOPE_NAME, JOURNAL_COLLECTION),
                "the journal collection must not exist when the feature is disabled");
    }

    @Test
    @DisplayName("journal enabled: an audit-only installation transparently creates the journal")
    void journalEnabledSplitsCurrentStateFromHistory() {
        FeatureFlag.enable(Features.JOURNAL_EVENTS);

        runPipeline(APPLIED("insert-document"));

        assertTrue(CouchbaseCollectionHelper.collectionExists(cluster, BUCKET_NAME, SCOPE_NAME,
                        CommunityPersistenceConstants.DEFAULT_AUDIT_STORE_NAME),
                "the existing audit collection must remain available");
        assertTrue(CouchbaseCollectionHelper.collectionExists(cluster, BUCKET_NAME, SCOPE_NAME, JOURNAL_COLLECTION));

        List<AuditEntry> auditRecords = new CouchbaseTestHelper(cluster)
                .getAuditEntriesSorted(cluster.bucket(BUCKET_NAME).scope(SCOPE_NAME)
                        .collection(CommunityPersistenceConstants.DEFAULT_AUDIT_STORE_NAME));
        assertEquals(1, auditRecords.size(), "the audit collection must retain only the current state when journal is enabled");
        assertEquals(AuditEntry.Status.APPLIED, auditRecords.get(0).getState());

        List<JournalEvent<AuditEntry>> events = storedEvents();
        assertEquals(2, events.size(), "one event must be stored for each audit state transition");
        assertTrue(events.stream().allMatch(event -> DEFAULT_STAGE_NAME.equals(event.getStreamId())),
                "journal events must use the pipeline stage as their stream");
        assertEquals(Arrays.asList(1L, 2L), events.stream()
                        .map(JournalEvent::getStreamSequence)
                        .sorted()
                        .collect(Collectors.toList()),
                "journal stream sequences must be contiguous from one");
        assertEquals(Arrays.asList(AuditEntry.Status.STARTED, AuditEntry.Status.APPLIED), events.stream()
                        .map(event -> event.getData().getState())
                        .sorted()
                        .collect(Collectors.toList()),
                "the journal must retain both audit state transitions");
    }

    private void runPipeline(AuditEntryExpectation... expectedAudits) {
        Bucket bucket = cluster.bucket(BUCKET_NAME);
        Collection testCollection = bucket.defaultCollection();
        AuditTestSupport.withTestKit(testKit)
                .GIVEN_Changes(new CodeChangeTestDefinition(
                        _002__insert_document.class,
                        Collections.singletonList(Collection.class)))
                .WHEN(() -> testKit.createBuilder()
                        .setAuditStore(auditStore)
                        .addTargetSystem(targetSystem)
                        .addDependency(testCollection)
                        .build()
                        .run())
                .THEN_VerifyAuditSequenceStrict(expectedAudits)
                .run();
    }

    private List<JournalEvent<AuditEntry>> storedEvents() {
        List<JournalEvent<AuditEntry>> events = new ArrayList<>();
        CouchbaseCollectionHelper.selectAllDocuments(cluster, BUCKET_NAME, SCOPE_NAME, JOURNAL_COLLECTION)
                .forEach(document -> events.add(mapper.fromDocument(document)));
        return events;
    }
}
