/*
 * Copyright 2023 Flamingock (https://www.flamingock.io)
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
package io.flamingock.internal.core.external.store.audit.community;

import io.flamingock.api.NonLockGuardedType;
import io.flamingock.api.annotations.NonLockGuarded;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.audit.AuditPersistence;
import io.flamingock.internal.common.core.feature.Features;
import io.flamingock.internal.core.external.store.AuditHistoryAppender;
import io.flamingock.internal.core.external.store.JournalHistoryAppender;
import io.flamingock.internal.util.FeatureFlag;
import io.flamingock.internal.util.Result;

/**
 * Community persistence with history contracts available during staged backend rollout.
 *
 * <p>History defaults explicitly reject unavailable operations without accessing storage. Backends must
 * override them to provide real support; ordinary persistence operations remain unchanged. RETURN-only
 * annotations preserve concrete outcomes without exempting method execution from lock guarding.
 */
public interface CommunityAuditPersistence extends AuditPersistence, CommunityAuditReader,
        AuditHistoryAppender, JournalHistoryAppender {

    /**
     * Rejects audit-history append until the backend overrides it, independently of {@code JOURNAL_EVENTS}.
     *
     * @param entry complete historical audit entry
     * @return the durable write outcome when implemented by the backend
     * @throws UnsupportedOperationException until the backend implements audit-history append
     */
    @Override
    @NonLockGuarded(NonLockGuardedType.RETURN)
    default Result append(AuditEntry entry) {
        throw new UnsupportedOperationException(
                "Audit-history append is not supported by this backend; implement append(AuditEntry) to enable it.");
    }

    /**
     * Rejects source-based journal append before storage initialization, reads, sequence allocation or writes
     * when disabled or not yet implemented by the backend. Backend overrides must preserve the feature gate
     * and own event construction and sequencing for this stage Persistence's journal destination, committing
     * only journal writes and confirming their owned sequencer only after durable success.
     *
     * @param payload source audit entry for the journal event
     * @return the durable journal write outcome when implemented by the backend
     * @throws IllegalStateException if {@code JOURNAL_EVENTS} is disabled
     * @throws UnsupportedOperationException until the backend implements source-based journal append
     */
    @Override
    @NonLockGuarded(NonLockGuardedType.RETURN)
    default Result appendEventFrom(AuditEntry payload) {
        if (!FeatureFlag.isEnabled(Features.JOURNAL_EVENTS)) {
            throw new IllegalStateException("Enable JOURNAL_EVENTS before appending journal history.");
        }
        throw new UnsupportedOperationException(
                "Journal-history append is not supported by this backend; implement appendEventFrom(AuditEntry).");
    }
}
