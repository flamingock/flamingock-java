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
import io.flamingock.core.kit.audit.AuditEntryTestFactory;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.audit.AuditTxType;
import io.flamingock.internal.common.core.context.ContextResolver;
import io.flamingock.internal.common.core.feature.Features;
import io.flamingock.internal.common.core.error.DatabaseTransactionException;
import io.flamingock.internal.common.core.journal.JournalEvent;
import io.flamingock.store.mongodb.sync.internal.MongoDBSyncJournalWriter;
import java.lang.reflect.Field;
import io.flamingock.internal.common.mongodb.MongoDBJournalEventMapper;
import io.flamingock.internal.core.configuration.community.CommunityConfigurable;
import io.flamingock.internal.core.configuration.community.CommunityConfiguration;
import io.flamingock.internal.util.FeatureFlag;
import io.flamingock.internal.util.id.RunnerId;
import io.flamingock.targetsystem.mongodb.sync.MongoDBSyncTargetSystem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Testcontainers
class MongoDBSyncHistoryProviderTest {
    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer(DockerImageName.parse("mongo:6"));

    private MongoClient client;
    private MongoDatabase database;
    private MongoDBSyncAuditStore store;

    @BeforeEach
    void setUp() {
        client = MongoClients.create(MONGO.getConnectionString());
        database = client.getDatabase("history-provider-test");
        MongoDBSyncTargetSystem target = new MongoDBSyncTargetSystem("mongodb", client, "history-provider-test");
        ContextResolver context = mock(ContextResolver.class);
        when(context.getRequiredDependencyValue(RunnerId.class)).thenReturn(RunnerId.generate());
        when(context.getRequiredDependencyValue(CommunityConfigurable.class)).thenReturn(new CommunityConfiguration());
        target.initialize(context);
        store = MongoDBSyncAuditStore.from(target);
        store.initialize(context);
        assertFalse(database.listCollectionNames().into(new ArrayList<>()).contains("flamingockJournalEvents"));
    }

    @AfterEach
    void tearDown() {
        FeatureFlag.remove(Features.JOURNAL_EVENTS);
        database.drop();
        client.close();
    }

    @Test
    void historicalAppenderWorksWithoutJournalFlag() {
        store.getAuditHistoryAppender().append(entry("one"));
        store.getAuditHistoryAppender().append(entry("one"));
        assertEquals(2, database.getCollection("flamingockAuditLog").countDocuments());
        assertEquals(2, store.getAuditReader().getAuditHistory().size());
        assertFalse(database.listCollectionNames().into(new ArrayList<>()).contains("flamingockJournalEvents"));
        assertThrows(IllegalStateException.class, () -> store.getJournalHistoryAppender().append("stage", entry("two")));
    }

    @Test
    void flagOffStagePersistenceWritesHistoricalAuditRows() {
        io.flamingock.internal.core.external.store.audit.community.CommunityAuditPersistence persistence =
                store.getPersistenceFactory().get("stage");
        persistence.writeEntry(entry("first"));
        persistence.writeEntry(entry("second"));

        assertEquals(2, persistence.getAuditHistory().size());
        assertFalse(database.listCollectionNames().into(new ArrayList<>()).contains("flamingockJournalEvents"));
    }

    @Test
    void earlyAppenderValidatesMissingAuditSchemaWhenAutoCreateIsDisabled() {
        database.drop();
        MongoDBSyncTargetSystem target = new MongoDBSyncTargetSystem("mongodb", client, "history-provider-test");
        ContextResolver context = mock(ContextResolver.class);
        when(context.getRequiredDependencyValue(RunnerId.class)).thenReturn(RunnerId.generate());
        when(context.getRequiredDependencyValue(CommunityConfigurable.class)).thenReturn(new CommunityConfiguration());
        target.initialize(context);
        MongoDBSyncAuditStore manualStore = MongoDBSyncAuditStore.from(target).withAutoCreate(false);
        assertThrows(RuntimeException.class, () -> manualStore.initialize(context));
        assertFalse(database.listCollectionNames().into(new ArrayList<>()).contains("flamingockAuditLog"));
    }

    @Test
    void journalOnlyDoesNotUpdateStateAndNormalWriteUsesNextPosition() {
        FeatureFlag.enable(Features.JOURNAL_EVENTS);
        initializeJournalEnabledStore();
        io.flamingock.internal.core.external.store.audit.community.CommunityAuditPersistence persistence =
                store.getPersistenceFactory().get("stage");
        persistence.writeEntry(entry("one"));
        store.getJournalHistoryAppender().append("stage", entry("two"));
        assertEquals(1, store.getAuditReader().getAuditHistory().size());
        persistence.writeEntry(entry("three"));
        assertEquals(2, store.getAuditReader().getAuditHistory().size());
        MongoDBJournalEventMapper mapper = new MongoDBJournalEventMapper();
        java.util.List<Long> sequences = new ArrayList<>();
        database.getCollection("flamingockJournalEvents").find().forEach(doc -> {
            assertEquals("CHANGE_STATE", mapper.fromDocument(doc).getEventType().name());
            sequences.add(mapper.fromDocument(doc).getStreamSequence());
        });
        assertEquals(java.util.Arrays.asList(1L, 2L, 3L), sequences);
    }

    @Test
    void failedJournalTransactionDoesNotConsumePosition() throws Exception {
        FeatureFlag.enable(Features.JOURNAL_EVENTS);
        initializeJournalEnabledStore();
        store.getJournalHistoryAppender().append("stage", entry("first"));
        Field writerField = MongoDBSyncAuditStore.class.getDeclaredField("journalWriter");
        writerField.setAccessible(true);
        MongoDBSyncJournalWriter original = (MongoDBSyncJournalWriter) writerField.get(store);
        MongoDBSyncJournalWriter failingWriter = org.mockito.Mockito.mock(MongoDBSyncJournalWriter.class);
        org.mockito.Mockito.doThrow(new IllegalStateException("failed insert"))
                .when(failingWriter).write(org.mockito.ArgumentMatchers.any(ClientSession.class),
                        org.mockito.ArgumentMatchers.any(io.flamingock.internal.core.journal.JournalEventSequencer.class),
                        org.mockito.ArgumentMatchers.any(AuditEntry.class));
        writerField.set(store, failingWriter);
        try {
            assertThrows(DatabaseTransactionException.class,
                    () -> store.getJournalHistoryAppender().append("stage", entry("failed")));
        } finally {
            writerField.set(store, original);
        }
        store.getJournalHistoryAppender().append("stage", entry("second"));
        assertEquals(0, store.getAuditReader().getAuditHistory().size());
        java.util.List<Long> positions = new ArrayList<>();
        MongoDBJournalEventMapper mapper = new MongoDBJournalEventMapper();
        database.getCollection("flamingockJournalEvents").find().forEach(doc ->
                positions.add(mapper.fromDocument(doc).getStreamSequence()));
        assertEquals(java.util.Arrays.asList(1L, 2L), positions);
    }

    private void initializeJournalEnabledStore() {
        MongoDBSyncTargetSystem target = new MongoDBSyncTargetSystem("mongodb", client, "history-provider-test");
        ContextResolver context = mock(ContextResolver.class);
        when(context.getRequiredDependencyValue(RunnerId.class)).thenReturn(RunnerId.generate());
        when(context.getRequiredDependencyValue(CommunityConfigurable.class)).thenReturn(new CommunityConfiguration());
        target.initialize(context);
        store = MongoDBSyncAuditStore.from(target);
        store.initialize(context);
        org.junit.jupiter.api.Assertions.assertTrue(
                database.listCollectionNames().into(new ArrayList<>()).contains("flamingockJournalEvents"));
    }

    private static AuditEntry entry(String id) {
        return AuditEntryTestFactory.createTestAuditEntry(id, AuditEntry.Status.APPLIED, AuditTxType.NON_TX,
                (Class<?>) null);
    }
}
