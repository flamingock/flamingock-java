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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns one sequencer per stream for this factory's lifetime. Persistence instances using the same
 * stream share confirmed sequence progress. The stage lock still serializes event writes;
 * sharing a sequencer does not make the allocation/write/confirmation cycle concurrent-safe.
 */
public class JournalEventSequencerFactory {

    private final JournalEventReader journalEventReader;
    private final Map<String, JournalEventSequencer> sequencersByStream = new ConcurrentHashMap<>();

    public JournalEventSequencerFactory(JournalEventReader journalEventReader) {
        this.journalEventReader = journalEventReader;
    }

    /**
     * Returns the shared sequencer for a stream, seeded from its durable tail on first access.
     *
     * @param streamId stream whose sequence positions are allocated
     * @return the same sequencer for every request for this stream within this factory
     */
    public JournalEventSequencer forStream(String streamId) {
        return sequencersByStream.computeIfAbsent(streamId, id -> {
            long initialSequence = journalEventReader.getLastEventByStream(id)
                    .map(e -> e.getStreamSequence() + 1)
                    .orElse(1L);
            return new JournalEventSequencer(id, initialSequence);
        });
    }
}
