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
package io.flamingock.internal.common.couchbase;

import com.couchbase.client.java.json.JsonObject;
import io.flamingock.core.kit.audit.AuditEntryTestFactory;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.audit.AuditTxType;
import io.flamingock.internal.common.core.journal.JournalEvent;
import io.flamingock.internal.common.core.journal.JournalEventType;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static io.flamingock.internal.common.couchbase.journal.JournalEventFieldConstants.KEY_IDEMPOTENCY_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CouchbaseJournalEventMapperTest {

    private final CouchbaseJournalEventMapper mapper = new CouchbaseJournalEventMapper();

    @Test
    void roundTripsIdempotencyKeyWithTheOtherJournalEventFields() {
        Instant occurredAt = Instant.parse("2026-09-18T00:00:00Z");
        AuditEntry auditEntry = AuditEntryTestFactory.createTestAuditEntry(
                "change-1", AuditEntry.Status.APPLIED, AuditTxType.NON_TX, (Class<?>) null);
        JournalEvent<AuditEntry> event = new JournalEvent<>(
                "event-1", "idempotency-key-1", JournalEventType.CHANGE_STATE, JournalEvent.DEFAULT_VERSION,
                "stream-1", 7L, occurredAt, auditEntry, true);

        JsonObject document = mapper.toDocument(event);
        JournalEvent<AuditEntry> restored = mapper.fromDocument(document);

        assertEquals("idempotency-key-1", document.getString(KEY_IDEMPOTENCY_KEY));
        assertEquals(event.getEventId(), restored.getEventId());
        assertEquals(event.getIdempotencyKey(), restored.getIdempotencyKey());
        assertEquals(event.getEventType(), restored.getEventType());
        assertEquals(event.getEventVersion(), restored.getEventVersion());
        assertEquals(event.getStreamId(), restored.getStreamId());
        assertEquals(event.getStreamSequence(), restored.getStreamSequence());
        assertEquals(event.getOccurredAt(), restored.getOccurredAt());
        assertEquals(event.isAcknowledged(), restored.isAcknowledged());
        assertEquals(event.getData().getChangeId(), restored.getData().getChangeId());
    }
}
