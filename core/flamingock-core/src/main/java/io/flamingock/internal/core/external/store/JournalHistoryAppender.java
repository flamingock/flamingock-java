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
package io.flamingock.internal.core.external.store;

import io.flamingock.api.NonLockGuardedType;
import io.flamingock.api.annotations.NonLockGuarded;
import io.flamingock.internal.common.core.journal.JournalEvent;
import io.flamingock.internal.core.journal.JournalEventSequencerFactory;
import io.flamingock.internal.util.Result;

/**
 * Independent capability for appending complete, caller-supplied journal events.
 *
 * <p>When {@code JOURNAL_EVENTS} is disabled, implementations must explicitly reject both append calls
 * and sequencer factory access without initializing or reading journal storage, allocating sequences or
 * writing events. Neither operation may implicitly activate journaling. Audit-history appending remains
 * independent of this gate. When journaling is enabled, storage must be initialized before reading its
 * durable tail; an uninitialized journal must not be treated as an empty stream.
 *
 * @param <T> payload type supported by the implementing backend; this generic boundary does not imply
 *            support for arbitrary backend payload types
 */
public interface JournalHistoryAppender<T> {

    /**
     * Appends the complete supplied event, preserving its payload, event metadata, destination stream and
     * stream sequence. The destination is not replaced by the executing stage. This operation does not
     * construct events, generate identifiers or sequences, re-sequence events or confirm sequencing.
     *
     * <p>The public append owns its journal-only transaction boundary and does not depend on an ambient
     * business transaction. A {@link Result.Ok} means durable completion, not an internal staging
     * acknowledgement. Unsuccessful contributions or commits must be propagated as an unsuccessful result
     * or a thrown failure, never converted into success. The caller confirms sequencing only after durable
     * success.
     *
     * <p>Only the returned outcome bypasses recursive lock guarding, preserving its concrete result type.
     * Execution of this method remains lock guarded.
     *
     * @param event complete event, including its intended destination and sequence
     * @return the durable write outcome
     * @throws IllegalStateException if {@code JOURNAL_EVENTS} is disabled
     */
    @NonLockGuarded(NonLockGuardedType.RETURN)
    Result append(JournalEvent<T> event);

    /**
     * Returns the store-owned concrete factory for sequencing the intended destination stream, which need
     * not be the executing stage. Factory access does not itself imply shared per-stream sequencing or a
     * distributed allocation guarantee.
     *
     * <p>The returned factory bypasses recursive lock guarding so sequencing helpers retain their concrete
     * behavior. Factory acquisition remains lock guarded, as does appending the resulting complete event.
     *
     * @return the store-owned sequencer factory
     * @throws IllegalStateException if {@code JOURNAL_EVENTS} is disabled
     */
    @NonLockGuarded(NonLockGuardedType.RETURN)
    JournalEventSequencerFactory getSequencerFactory();
}
