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
package io.flamingock.internal.core.external.store.journal;

import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.journal.JournalEvent;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Persistence abstraction for the local durable event buffer (see ADR-0001).
 * <p>
 * Each audit store provides its own implementation (MongoDB, SQL, DynamoDB, Couchbase, …) so upstream
 * callers depend on this contract rather than a concrete store. The buffer holds immutable facts awaiting
 * synchronization to Flamingock Cloud; it is a separate concern from the operational/audit state.
 * <p>
 * Phase 1 exposes read/acknowledge only. Event <em>writing</em> (atomic with the state write) is a later phase.
 */
public interface JournalEventStore {

    /**
     * Returns the event with the highest {@code streamSequence} for the given stream, if any.
     */
    Optional<JournalEvent<AuditEntry>> getLastEventByStream(String streamId);

    /**
     * Returns up to {@code limit} events that have not yet been acknowledged, in best-effort delivery order.
     */
    List<JournalEvent<AuditEntry>> getUnacknowledgedEvents(int limit);

    /**
     * Marks the events with the given ids as acknowledged and returns how many were updated.
     */
    long acknowledgeEvents(Collection<String> eventIds);
}
