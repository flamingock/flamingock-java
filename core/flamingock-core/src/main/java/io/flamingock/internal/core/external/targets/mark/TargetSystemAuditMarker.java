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
package io.flamingock.internal.core.external.targets.mark;

import io.flamingock.internal.common.cloud.vo.TargetSystemAuditMarkType;
import io.flamingock.internal.common.core.error.FlamingockException;
import io.flamingock.internal.core.task.executable.ExecutableTask;
import java.util.Set;

/**
 * Local repository that persists lightweight {@link TargetSystemAuditMark} entries indicating that a
 * Change has been successfully applied on a transactional Target System.
 * <p>
 * This repository acts as a <em>local advisory source of truth</em> used during reconciliation with the
 * remote Audit Store (e.g., in the Cloud edition). Its purpose is to reduce false negatives caused by
 * incomplete or delayed remote audit state. Presence of a mark strongly indicates that the change was
 * applied locally; absence is <strong>inconclusive</strong> and may require manual intervention.
 * <p>
 * Characteristics:
 * <ul>
 *   <li><strong>Advisory, not critical:</strong> Missing marks do not corrupt the system; they only
 *       reduce the ability to auto-resolve discrepancies.</li>
 *   <li><strong>Durable enough for recovery:</strong> Implementations should persist marks so they
 *       survive process crashes until synchronization is completed.</li>
 *   <li><strong>Idempotent operations:</strong> Repeated inserts for the same change must be safe; removals
 *       should be no-ops if the mark does not exist.</li>
 * </ul>
 *
 */
public interface TargetSystemAuditMarker {

    /**
     * Returns all known local audit marks for the Target System.
     * <p>
     *
     * @return a non-null, possibly empty set of {@link TargetSystemAuditMark}.
     * @throws FlamingockException if the underlying storage cannot be read.
     */
    Set<TargetSystemAuditMark> listAll();

    /**
     * Removes the local audit mark for the given change identifier.
     * <p>
     * This is a hygienic cleanup step that should be invoked after the remote Audit Store has
     * acknowledged the change, but not critical. It is idempotent: if no mark exists for {@code changeId}, the call
     * completes without error.
     *
     * @param changeId the identifier of the change whose local mark should be removed.
     * @throws FlamingockException if the operation fails (e.g., storage unavailable).
     */
    void clearMark(String changeId);

    /**
     * Creates or updates a local audit mark.
     * <p>
     * Implementations should ensure this operation is idempotent. The write operation must
     * participate in the same transaction as the Target System operation that is being confirmed.
     *
     * @param auditMark the mark to persist.
     * @throws FlamingockException if the operation fails (e.g., storage unavailable).
     */
    void mark(TargetSystemAuditMark auditMark);

    /**
     * Convenience method to record that the given Change has been applied on the local
     * Target System. Equivalent to calling {@link #mark(TargetSystemAuditMark)} with a mark of type
     * {@link TargetSystemAuditMarkType#APPLIED}.
     * <p>
     * Implementations should ensure this operation is idempotent. The write operation must
     * participate in the same transaction as the Target System operation that is being confirmed.
     *
     * @param change the applied change to be marked locally.
     * @throws FlamingockException if the operation fails (e.g., storage unavailable).
     */
    default void markApplied(ExecutableTask change) {
        mark(new TargetSystemAuditMark(change.getId(), TargetSystemAuditMarkType.APPLIED));
    }
}
