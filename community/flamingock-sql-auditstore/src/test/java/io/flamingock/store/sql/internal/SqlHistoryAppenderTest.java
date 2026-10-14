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
package io.flamingock.store.sql.internal;

import io.flamingock.core.kit.audit.AuditEntryTestFactory;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.audit.AuditTxType;
import io.flamingock.internal.common.core.feature.Features;
import io.flamingock.internal.core.configuration.community.CommunityConfiguration;
import io.flamingock.internal.core.journal.JournalEventSequencerFactory;
import io.flamingock.internal.core.transaction.TransactionManager;
import io.flamingock.internal.util.FeatureFlag;
import io.flamingock.targetsystem.sql.SqlTxWrapper;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlHistoryAppenderTest {
    private SqlAuditPersistence persistence;
    private SqlJournalEventStore journal;

    @BeforeEach
    void setUp() {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        SqlTxWrapper wrapper = new SqlTxWrapper(new TransactionManager<>(() -> {
            try {
                return source.getConnection();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }));
        SqlAuditRepository repository = new SqlAuditRepository(source, "auditHistory");
        repository.initialize(true);
        journal = org.mockito.Mockito.spy(new SqlJournalEventStore(source, "journalHistory", wrapper));
        journal.initialize(true);
        persistence = new SqlAuditPersistence(new CommunityConfiguration(), repository, journal,
                new JournalEventSequencerFactory(journal).forStream("destination"), wrapper, true);
    }

    @AfterEach
    void tearDown() {
        FeatureFlag.remove(Features.JOURNAL_EVENTS);
    }

    @Test
    void historicalAppendRetainsTransitionsWithFlagOnAndOff() {
        AuditEntry started = entry(AuditEntry.Status.STARTED);
        AuditEntry applied = entry(AuditEntry.Status.APPLIED);
        FeatureFlag.enable(Features.JOURNAL_EVENTS);
        assertFalse(persistence.append(started).isError());
        FeatureFlag.remove(Features.JOURNAL_EVENTS);
        assertFalse(persistence.append(applied).isError());
        assertEquals(2, persistence.getAuditHistory().size());
        assertTrue(persistence.getAuditHistory().stream().anyMatch(e -> e.getState() == started.getState()
                && e.getExecutionId().equals(started.getExecutionId())));
        assertTrue(journal.getUnacknowledgedEvents(10).isEmpty());
    }

    @Test
    void sourceEventsUsePersistenceStreamAndDoNotWriteAudit() {
        FeatureFlag.enable(Features.JOURNAL_EVENTS);
        AuditEntry payload = entry(AuditEntry.Status.APPLIED);
        assertFalse(persistence.appendEventFrom(payload).isError());
        assertFalse(persistence.appendEventFrom(payload).isError());
        assertEquals(2L, journal.getLastEventByStream("destination").get().getStreamSequence());
        assertEquals(payload.getExecutionId(), journal.getLastEventByStream("destination").get()
                .getData().getExecutionId());
        assertFalse(journal.getLastEventByStream(payload.getStageId()).isPresent());
        assertTrue(persistence.getAuditHistory().isEmpty());
    }

    @Test
    void disabledSourceAppendLeavesHistoryAndSequenceUnchanged() {
        assertThrows(IllegalStateException.class,
                () -> persistence.appendEventFrom(entry(AuditEntry.Status.APPLIED)));
        assertTrue(persistence.getAuditHistory().isEmpty());
        assertTrue(journal.getUnacknowledgedEvents(10).isEmpty());
        FeatureFlag.enable(Features.JOURNAL_EVENTS);
        persistence.appendEventFrom(entry(AuditEntry.Status.APPLIED));
        assertEquals(1L, journal.getUnacknowledgedEvents(10).get(0).getStreamSequence());
    }

    @Test
    void failedContributionDoesNotConsumeSequenceOrWriteAudit() {
        FeatureFlag.enable(Features.JOURNAL_EVENTS);
        org.mockito.Mockito.doThrow(new IllegalStateException("injected journal failure"))
                .doCallRealMethod().when(journal).append(org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any());
        assertThrows(io.flamingock.internal.common.core.error.DatabaseTransactionException.class,
                () -> persistence.appendEventFrom(entry(AuditEntry.Status.APPLIED)));
        assertTrue(journal.getUnacknowledgedEvents(10).isEmpty());
        assertTrue(persistence.getAuditHistory().isEmpty());
        persistence.appendEventFrom(entry(AuditEntry.Status.APPLIED));
        assertEquals(1L, journal.getUnacknowledgedEvents(10).get(0).getStreamSequence());
    }

    @Test
    void normalWritesRemainOnDestinationAndUpdateCurrentState() {
        persistence.writeEntry(entry(AuditEntry.Status.STARTED));
        persistence.writeEntry(entry(AuditEntry.Status.APPLIED));
        assertEquals(1, persistence.getAuditHistory().size());
        assertEquals(AuditEntry.Status.APPLIED, persistence.getAuditHistory().get(0).getState());
        assertEquals(2L, journal.getLastEventByStream("destination").get().getStreamSequence());
    }

    private AuditEntry entry(AuditEntry.Status status) {
        return AuditEntryTestFactory.createTestAuditEntry("historical-change", status, AuditTxType.NON_TX,
                (Class<?>) null);
    }
}
