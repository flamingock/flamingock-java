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
package io.flamingock.core.kit.inmemory;

import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.journal.JournalEvent;
import io.flamingock.internal.core.journal.JournalEventStore;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * In-memory implementation of the local journal ({@link JournalEventStore}), the in-memory counterpart to
 * {@code MongoDBSyncJournalEventStore} / {@code SqlJournalEventStore} / {@code DynamoDBJournalEventStore}.
 *
 * <p>Backs {@code InternalInMemoryTestAuditStore} when {@code Features.JOURNAL_EVENTS} is enabled, so
 * {@code e2e/core-e2e} tests can assert the STARTED&rarr;APPLIED/FAILED/ROLLED_BACK progression against the
 * journal instead of the audit log, matching what the real audit stores do once the flag flips the audit log
 * over to current-state-per-change.
 *
 * <p>No transaction handle is needed here — unlike the real stores, there is no separate transactional resource
 * for the append to join, so {@link #write(JournalEvent)} is a plain synchronized add.</p>
 */
public class InternalInMemoryJournalEventStore implements JournalEventStore {

    private final List<JournalEvent<AuditEntry>> events = new ArrayList<>();

    /**
     * Appends an event to the journal. Package-visible, like the real stores' append methods: only
     * {@code InternalInMemoryTestAuditPersistence} (the persistence owning the write) calls it.
     */
    synchronized void write(JournalEvent<AuditEntry> event) {
        events.add(event);
    }

    @Override
    public synchronized Optional<JournalEvent<AuditEntry>> getLastEventByStream(String streamId) {
        return events.stream()
                .filter(event -> streamId.equals(event.getStreamId()))
                .max(Comparator.comparingLong(JournalEvent::getStreamSequence));
    }

    @Override
    public synchronized List<JournalEvent<AuditEntry>> getUnacknowledgedEvents(int limit) {
        return events.stream()
                .filter(event -> !event.isAcknowledged())
                .limit(limit)
                .collect(Collectors.toList());
    }

    @Override
    public synchronized long acknowledgeEvents(Collection<String> eventIds) {
        if (eventIds == null || eventIds.isEmpty()) {
            return 0L;
        }
        long count = 0;
        for (JournalEvent<AuditEntry> event : events) {
            if (!event.isAcknowledged() && eventIds.contains(event.getEventId())) {
                event.acknowledge();
                count++;
            }
        }
        return count;
    }

    /**
     * Returns every event recorded so far, for test assertions.
     */
    public synchronized List<JournalEvent<AuditEntry>> getAllEvents() {
        return new ArrayList<>(events);
    }

    /**
     * Returns the events for one stream, in sequence order, for test assertions.
     */
    public synchronized List<JournalEvent<AuditEntry>> getEventsForStream(String streamId) {
        return events.stream()
                .filter(event -> streamId.equals(event.getStreamId()))
                .sorted(Comparator.comparingLong(JournalEvent::getStreamSequence))
                .collect(Collectors.toList());
    }

    public synchronized void clear() {
        events.clear();
    }
}
