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
package io.flamingock.internal.core.journal;

import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.journal.JournalEvent;
import io.flamingock.internal.common.core.journal.JournalEventType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JournalEventSequencerFactoryTest {

    private final JournalEventReader reader = mock(JournalEventReader.class);
    private final AuditEntry payload = mock(AuditEntry.class);
    private final JournalEventSequencerFactory factory = new JournalEventSequencerFactory(reader);

    @Test
    @DisplayName("shares confirmed sequence progress between consumers of the same stream")
    void sharesSequenceProgressBetweenConsumers() {
        when(reader.getLastEventByStream("stage-1")).thenReturn(Optional.empty());
        JournalEventSequencer outer = factory.forStream("stage-1");
        assertEquals(1L, outer.newEvent(payload).getStreamSequence());
        outer.confirm();

        when(reader.getLastEventByStream("stage-1")).thenReturn(Optional.of(event("stage-1", 1L)));
        JournalEventSequencer inner = factory.forStream("stage-1");
        assertEquals(2L, inner.newEvent(payload).getStreamSequence());
        inner.confirm();

        assertEquals(3L, outer.newEvent(payload).getStreamSequence());
        outer.confirm();
        assertSame(outer, inner);
        assertSame(outer, factory.forStream("stage-1"));
        assertEquals(4L, factory.forStream("stage-1").newEvent(payload).getStreamSequence());
    }

    @Test
    @DisplayName("seeds independent streams from their own durable tails")
    void seedsIndependentStreamsFromDurableTails() {
        when(reader.getLastEventByStream("stage-1")).thenReturn(Optional.of(event("stage-1", 7L)));
        when(reader.getLastEventByStream("stage-2")).thenReturn(Optional.empty());
        JournalEventSequencer first = factory.forStream("stage-1");
        JournalEventSequencer second = factory.forStream("stage-2");

        assertNotSame(first, second);
        JournalEvent<AuditEntry> firstEvent = first.newEvent(payload);
        assertEquals("stage-1", firstEvent.getStreamId());
        assertEquals(8L, firstEvent.getStreamSequence());
        first.confirm();
        JournalEvent<AuditEntry> secondEvent = second.newEvent(payload);
        assertEquals("stage-2", secondEvent.getStreamId());
        assertEquals(1L, secondEvent.getStreamSequence());
        assertEquals(9L, first.newEvent(payload).getStreamSequence());
    }

    @Test
    @DisplayName("reuses an unconfirmed position through another consumer")
    void reusesUnconfirmedPosition() {
        when(reader.getLastEventByStream("stage-1")).thenReturn(Optional.empty());
        JournalEventSequencer first = factory.forStream("stage-1");
        assertEquals(1L, first.newEvent(payload).getStreamSequence());

        JournalEventSequencer second = factory.forStream("stage-1");
        assertEquals(1L, second.newEvent(payload).getStreamSequence());
        second.confirm();
        assertEquals(2L, first.newEvent(payload).getStreamSequence());
    }

    private JournalEvent<AuditEntry> event(String streamId, long sequence) {
        return new JournalEvent<>("event-" + sequence, "key-" + sequence, JournalEventType.CHANGE_STATE,
                streamId, sequence, Instant.now(), payload);
    }
}
