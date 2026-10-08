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
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.util.Result;

/**
 * Independent capability for appending journal events from source payloads.
 *
 * <p>When {@code JOURNAL_EVENTS} is disabled, implementations must explicitly reject calls before journal
 * storage initialization, reads, sequence allocation or writes. This operation must not implicitly activate
 * journaling. Audit-history appending remains independent of this gate. When journaling is enabled, storage
 * must be initialized before reading its durable tail; an uninitialized journal is not an empty stream.
 *
 * <p>Supported source payload types are declared by explicit overloads. Only {@link AuditEntry} is supported
 * currently; this contract does not imply support for arbitrary payload types.
 */
public interface JournalHistoryAppender {

    /**
     * Constructs and appends a journal event from the supplied source payload. The stage Persistence selected
     * through the existing factory determines the journal destination. The backend constructs the event
     * envelope and owns sequence allocation and confirmation; callers do not supply a complete journal event.
     *
     * <p>This operation owns a journal-only durable transaction, does not depend on an ambient business
     * transaction and must not append or update audit rows. A {@link Result.Ok} means durable commit, not an
     * internal staging acknowledgement. Unsuccessful contributions or commits must be propagated as an
     * unsuccessful result or a thrown failure, never converted into success. The backend confirms its owned
     * sequencer only after successful durable completion, never after a failed write.
     *
     * <p>Only the returned outcome bypasses recursive lock guarding, preserving its concrete result type.
     * Execution of this method remains lock guarded.
     *
     * @param payload source audit entry for the journal event
     * @return the durable journal write outcome
     * @throws IllegalStateException if {@code JOURNAL_EVENTS} is disabled
     * @throws UnsupportedOperationException if the backend does not support this operation
     */
    @NonLockGuarded(NonLockGuardedType.RETURN)
    Result appendEventFrom(AuditEntry payload);
}
