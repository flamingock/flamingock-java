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

import io.flamingock.internal.common.core.feature.Features;
import io.flamingock.internal.util.FeatureFlag;

public class JournalEventSequencerFactory {

    private final JournalEventStore journalEventStore;

    public JournalEventSequencerFactory(JournalEventStore journalEventStore) {
        this.journalEventStore = journalEventStore;
    }

    public JournalEventSequencer forStream(String streamId) {
        long initialSequence = journalEventStore.getLastEventByStream(streamId)
                .map(e -> e.getStreamSequence() + 1)
                .orElse(1L);
        return new JournalEventSequencer(streamId, initialSequence);
    }

    /**
     * Centralizes the per-stage journal initialization shared by every community audit store:
     * when the {@link Features#JOURNAL_EVENTS} feature flag is on, ensures the journal store exists
     * (creating it when {@code autoCreate}) and returns a sequencer for the given stage; otherwise
     * returns {@code null} and leaves the journal store untouched.
     */
    public JournalEventSequencer initializeForStage(String stageId, boolean autoCreate) {
        if (!isJournalEventsEnabled()) {
            return null;
        }
        journalEventStore.initialize(autoCreate);
        return forStream(stageId);
    }

    private static boolean isJournalEventsEnabled() {
        try {
            return FeatureFlag.isEnabled(Features.JOURNAL_EVENTS, false);
        } catch (RuntimeException exception) {
            return false;
        }
    }
}
