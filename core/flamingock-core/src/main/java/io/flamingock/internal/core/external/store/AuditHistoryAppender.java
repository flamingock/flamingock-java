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

import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.util.Result;

/**
 * Independent capability for appending caller-supplied audit history.
 *
 * <p>This capability remains available independently of {@code JOURNAL_EVENTS}. Appending audit history
 * does not implicitly activate journal storage, construct journal events or allocate journal sequences.
 */
public interface AuditHistoryAppender {

    /**
     * Appends the supplied historical entry without replacing its payload, historical fields or destination
     * with values from the executing stage. The entry is appended to history rather than collapsed into
     * the current audit state.
     *
     * <p>A {@link Result.Ok} means the write has completed durably, not merely been staged in a transaction.
     * Unsuccessful writes must be propagated as an unsuccessful result or a thrown failure; they must not
     * be converted into success.
     *
     * @param entry complete historical entry, including its intended destination
     * @return the durable write outcome
     */
    Result append(AuditEntry entry);
}
