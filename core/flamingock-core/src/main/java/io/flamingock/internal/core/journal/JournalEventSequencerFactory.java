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

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class JournalEventSequencerFactory {

    private final JournalEventReader journalEventReader;
    private final ConcurrentMap<String, JournalEventSequencer> sequencers = new ConcurrentHashMap<>();

    public JournalEventSequencerFactory(JournalEventReader journalEventReader) {
        this.journalEventReader = journalEventReader;
    }

    public JournalEventSequencer forStream(String streamId) {
        return sequencers.computeIfAbsent(streamId, id -> {
            long initialSequence = journalEventReader.getLastEventByStream(id)
                    .map(e -> Math.addExact(e.getStreamSequence(), 1L))
                    .orElse(1L);
            return new JournalEventSequencer(id, initialSequence, journalEventReader);
        });
    }
}
