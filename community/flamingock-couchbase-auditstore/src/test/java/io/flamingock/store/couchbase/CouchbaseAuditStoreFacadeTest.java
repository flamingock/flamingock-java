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
import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.ClusterOptions;
import io.flamingock.core.kit.audit.AuditEntryTestFactory;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.audit.AuditTxType;
import io.flamingock.internal.common.core.context.ContextResolver;
import io.flamingock.internal.common.core.feature.Features;
import io.flamingock.internal.common.core.journal.JournalEvent;
import io.flamingock.internal.common.couchbase.CouchbaseCollectionHelper;
import io.flamingock.internal.common.couchbase.CouchbaseJournalEventMapper;
import io.flamingock.internal.core.configuration.community.CommunityConfigurable;
import io.flamingock.internal.core.configuration.community.CommunityConfiguration;
import io.flamingock.internal.util.FeatureFlag;
import io.flamingock.internal.util.id.RunnerId;
import io.flamingock.targetsystem.couchbase.CouchbaseTargetSystem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.couchbase.BucketDefinition;
import org.testcontainers.couchbase.CouchbaseContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Testcontainers
class CouchbaseAuditStoreFacadeTest {
    private static final String BUCKET = "test";
    private static final String SCOPE = CollectionIdentifier.DEFAULT_SCOPE;
    private static final String AUDIT = "facadeAudit";
    private static final String LOCK = "facadeLock";
    private static final String JOURNAL = "facadeJournal";
    private static final String STREAM = "facade-stage";

    @Container
    static final CouchbaseContainer container = new CouchbaseContainer("couchbase/server:7.2.4")
            .withBucket(new BucketDefinition(BUCKET));
    private static Cluster cluster;
    private CouchbaseAuditStore store;

    @BeforeAll
    static void connect() {
        container.start();
        cluster = Cluster.connect(container.getConnectionString(),
                ClusterOptions.clusterOptions(container.getUsername(), container.getPassword())
                        .environment(env -> env.timeoutConfig(timeout -> timeout.kvTimeout(Duration.ofSeconds(10)))));
        cluster.bucket(BUCKET).waitUntilReady(Duration.ofSeconds(10));
    }

    private void initialize(boolean journalEnabled) {
        if (journalEnabled) {
            FeatureFlag.enable(Features.JOURNAL_EVENTS);
        }
        ContextResolver context = mock(ContextResolver.class);
        when(context.getDependencyValue(io.flamingock.internal.core.builder.FlamingockEdition.class))
                .thenReturn(Optional.empty());
        when(context.getRequiredDependencyValue(RunnerId.class)).thenReturn(RunnerId.generate());
        when(context.getRequiredDependencyValue(CommunityConfigurable.class)).thenReturn(new CommunityConfiguration());
        CouchbaseTargetSystem target = new CouchbaseTargetSystem("couchbase", cluster, BUCKET);
        store = CouchbaseAuditStore.from(target).withAuditRepositoryName(AUDIT)
                .withLockRepositoryName(LOCK).withJournalRepositoryName(JOURNAL);
        target.initialize(context);
        store.initialize(context);
    }

    @AfterEach
    void cleanup() {
        FeatureFlag.remove(Features.JOURNAL_EVENTS);
        CouchbaseCollectionHelper.dropCollectionIfExists(cluster, BUCKET, SCOPE, AUDIT);
        CouchbaseCollectionHelper.dropCollectionIfExists(cluster, BUCKET, SCOPE, LOCK);
        CouchbaseCollectionHelper.dropCollectionIfExists(cluster, BUCKET, SCOPE, JOURNAL);
    }

    @Test
    void disabledAppenderPreservesHistoryAndRejectsJournalWrites() {
        initialize(false);
        AuditEntry started = entry("change", AuditEntry.Status.STARTED);
        AuditEntry applied = entry("change", AuditEntry.Status.APPLIED);
        store.getAuditHistoryAppender().append(started);
        store.getAuditHistoryAppender().append(applied);

        assertEquals(2, store.getAuditReader().getAuditHistory().size());
        assertThrows(IllegalStateException.class, () -> store.getJournalHistoryAppender().append(STREAM, applied));
        assertFalse(CouchbaseCollectionHelper.collectionExists(cluster, BUCKET, SCOPE, JOURNAL));
    }

    @Test
    void enabledJournalOnlyAndNormalWriteShareEventConstruction() {
        initialize(true);
        // Stage persistence is built before the journal-only append, so both paths must share its sequencer.
        io.flamingock.internal.core.external.store.audit.community.CommunityAuditPersistence persistence =
                store.getPersistenceFactory().get(STREAM);
        AuditEntry first = entry("first", AuditEntry.Status.STARTED);
        persistence.writeEntry(first);
        AuditEntry journalOnly = entry("journal-only", AuditEntry.Status.STARTED);
        store.getJournalHistoryAppender().append(STREAM, journalOnly);
        assertEquals(1, store.getAuditReader().getAuditHistory().size());

        AuditEntry normal = entry("normal", AuditEntry.Status.APPLIED);
        persistence.writeEntry(normal);
        assertEquals(2, store.getAuditReader().getAuditHistory().size());

        List<JournalEvent<AuditEntry>> events = new ArrayList<>();
        CouchbaseJournalEventMapper mapper = new CouchbaseJournalEventMapper();
        CouchbaseCollectionHelper.selectAllDocuments(cluster, BUCKET, SCOPE, JOURNAL)
                .forEach(document -> events.add(mapper.fromDocument(document)));
        assertEquals(3, events.size());
        events.sort((left, right) -> Long.compare(left.getStreamSequence(), right.getStreamSequence()));
        assertEquals(1L, events.get(0).getStreamSequence());
        assertEquals(2L, events.get(1).getStreamSequence());
        assertEquals(3L, events.get(2).getStreamSequence());
        assertEquals(events.get(0).getEventType(), events.get(1).getEventType());
        assertEquals(events.get(0).getEventVersion(), events.get(1).getEventVersion());
        assertEquals(STREAM, events.get(0).getStreamId());
        assertEquals(STREAM, events.get(1).getStreamId());
        assertEquals(STREAM, events.get(2).getStreamId());
        assertEquals("first", events.get(0).getData().getChangeId());
        assertEquals("journal-only", events.get(1).getData().getChangeId());
        assertEquals("normal", events.get(2).getData().getChangeId());
        assertFalse(events.get(0).isAcknowledged());
        assertFalse(events.get(1).isAcknowledged());
        assertFalse(events.get(2).isAcknowledged());
    }

    private static AuditEntry entry(String changeId, AuditEntry.Status status) {
        return AuditEntryTestFactory.createTestAuditEntry(changeId, status, AuditTxType.NON_TX, (Class<?>) null);
    }
}
