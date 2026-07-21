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

import io.flamingock.api.RecoveryStrategy;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.audit.AuditTxType;
import io.flamingock.internal.common.core.event.FlamingockEvent;
import io.flamingock.internal.common.core.event.FlamingockEventType;
import org.bson.Document;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MongoDBEventMapperTest {

    private final MongoDBEventMapper mapper = new MongoDBEventMapper();

    @Test
    void roundTripsChangeStateEventWithAuditEntryPayload() {
        Instant occurredAt = Instant.parse("2026-07-21T10:15:30Z");
        FlamingockEvent<AuditEntry> event = new FlamingockEvent<>(
                "evt-1",
                FlamingockEventType.CHANGE_STATE,
                FlamingockEvent.DEFAULT_VERSION,
                "stageA",
                7L,
                occurredAt,
                auditEntry("change-1"),
                true);

        FlamingockEvent<AuditEntry> restored = mapper.fromDocument(mapper.toDocument(event));

        assertEquals("evt-1", restored.getEventId());
        assertEquals(FlamingockEventType.CHANGE_STATE, restored.getEventType());
        assertEquals(FlamingockEvent.DEFAULT_VERSION, restored.getEventVersion());
        assertEquals("stageA", restored.getStreamId());
        assertEquals(7L, restored.getStreamSequence());
        assertEquals(occurredAt, restored.getOccurredAt());
        assertEquals(true, restored.isAcknowledged());
        assertEquals("change-1", restored.getData().getChangeId());
        assertEquals(AuditEntry.Status.APPLIED, restored.getData().getState());
    }

    @Test
    void toDocumentRejectsUnsupportedEventType() {
        FlamingockEvent<AuditEntry> executionEvent = new FlamingockEvent<>(
                "evt-2",
                FlamingockEventType.EXECUTION_STATE,
                FlamingockEvent.DEFAULT_VERSION,
                "stageA",
                1L,
                Instant.parse("2026-07-21T10:15:30Z"),
                auditEntry("change-2"),
                false);

        assertThrows(UnsupportedOperationException.class, () -> mapper.toDocument(executionEvent));
    }

    @Test
    void fromDocumentRejectsUnsupportedEventType() {
        Document stored = new Document("eventType", FlamingockEventType.EXECUTION_STATE.name())
                .append("eventId", "evt-3");

        assertThrows(UnsupportedOperationException.class, () -> mapper.fromDocument(stored));
    }

    private static AuditEntry auditEntry(String changeId) {
        return new AuditEntry(
                "exec-1",
                "stage-1",
                changeId,
                "tester",
                LocalDateTime.now(),
                AuditEntry.Status.APPLIED,
                AuditEntry.ChangeType.STANDARD_CODE,
                "TestClass",
                "testMethod",
                "TestSourceFile",
                0L,
                "test-hostname",
                null,
                false,
                null,
                AuditTxType.NON_TX,
                null,
                "001",
                RecoveryStrategy.MANUAL_INTERVENTION,
                null);
    }
}
