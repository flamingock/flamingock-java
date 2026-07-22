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
package io.flamingock.internal.common.mongodb;

import io.flamingock.core.kit.audit.AuditEntryTestFactory;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.audit.AuditTxType;
import io.flamingock.internal.common.core.journal.JournalEvent;
import io.flamingock.internal.common.core.journal.JournalEventType;
import org.bson.Document;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MongoDBJournalEventMapperTest {

    private final MongoDBJournalEventMapper mapper = new MongoDBJournalEventMapper();

    @Test
    void roundTripsChangeStateEventWithAuditEntryPayload() {
        Instant occurredAt = Instant.parse("2026-07-21T10:15:30Z");
        JournalEvent<AuditEntry> event = new JournalEvent<>(
                "evt-1",
                JournalEventType.CHANGE_STATE,
                JournalEvent.DEFAULT_VERSION,
                "stageA",
                7L,
                occurredAt,
                auditEntry("change-1"),
                true);

        JournalEvent<AuditEntry> restored = mapper.fromDocument(mapper.toDocument(event));

        assertEquals("evt-1", restored.getEventId());
        assertEquals(JournalEventType.CHANGE_STATE, restored.getEventType());
        assertEquals(JournalEvent.DEFAULT_VERSION, restored.getEventVersion());
        assertEquals("stageA", restored.getStreamId());
        assertEquals(7L, restored.getStreamSequence());
        assertEquals(occurredAt, restored.getOccurredAt());
        assertTrue(restored.isAcknowledged());
        assertEquals("change-1", restored.getData().getChangeId());
        assertEquals(AuditEntry.Status.APPLIED, restored.getData().getState());
    }

    @Test
    void storesEventFieldsUnderTheAgreedBsonNames() {
        JournalEvent<AuditEntry> event = new JournalEvent<>(
                "evt-4",
                JournalEventType.CHANGE_STATE,
                "stageA",
                3L,
                Instant.parse("2026-07-21T10:15:30Z"),
                auditEntry("change-4"));

        Document document = mapper.toDocument(event);

        assertEquals("evt-4", document.getString("eventId"));
        assertEquals("stageA", document.getString("streamId"));
        assertEquals(3L, document.get("streamSequence"));
        // acknowledged must be persisted as a real boolean false: the partial index filters on it.
        assertEquals(Boolean.FALSE, document.get("acknowledged"));
        assertTrue(document.get("data") instanceof Document);
    }

    @Test
    void toDocumentRejectsUnsupportedEventType() {
        JournalEvent<AuditEntry> executionEvent = new JournalEvent<>(
                "evt-2",
                JournalEventType.EXECUTION_STATE,
                JournalEvent.DEFAULT_VERSION,
                "stageA",
                1L,
                Instant.parse("2026-07-21T10:15:30Z"),
                auditEntry("change-2"),
                false);

        assertThrows(UnsupportedOperationException.class, () -> mapper.toDocument(executionEvent));
    }

    @Test
    void fromDocumentRejectsUnsupportedEventType() {
        Document stored = new Document("eventType", JournalEventType.EXECUTION_STATE.name())
                .append("eventId", "evt-3");

        assertThrows(UnsupportedOperationException.class, () -> mapper.fromDocument(stored));
    }

    private static AuditEntry auditEntry(String changeId) {
        return AuditEntryTestFactory.createTestAuditEntry(
                changeId, AuditEntry.Status.APPLIED, AuditTxType.NON_TX, (Class<?>) null);
    }
}
