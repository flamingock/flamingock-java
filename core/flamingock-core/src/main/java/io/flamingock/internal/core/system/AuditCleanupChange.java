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
package io.flamingock.internal.core.system;

import io.flamingock.api.annotations.NonLockGuarded;
import io.flamingock.internal.common.core.error.FlamingockException;
import io.flamingock.internal.common.core.feature.Features;
import io.flamingock.internal.core.external.store.AuditCompactor;
import io.flamingock.internal.util.FeatureFlag;
import io.flamingock.internal.util.Result;
import io.flamingock.internal.util.log.FlamingockLoggerFactory;
import org.slf4j.Logger;

/**
 * The system change that collapses the audit store to one record per change, once the journal holds the
 * history that the audit store used to carry.
 *
 * <h2>NOT YET WIRED — read this before assuming it runs</h2>
 * This class is <b>deliberately not contributed to any pipeline.</b> Nothing registers it as a system
 * change, so it never appears in a stage and never executes. It exists so that the per-store
 * {@code AuditCompactor} implementations have a consumer ready and reviewed, and so the eventual wiring is
 * a small, obvious change rather than a large one.
 * <p>
 * Two things must happen before it can be wired, in this order:
 * <ol>
 *   <li><b>The journal backfill system change must exist.</b> Compaction <em>deletes</em> the audit ledger;
 *       the backfill is what first preserves it as {@code JournalEvent}s. If this change ever runs before
 *       that one has, the change history is gone — not in the audit store, not in the journal, and
 *       unrecoverable. The ordering guarantee is real once both exist (the system stage sorts by
 *       {@code order} and runs before every other stage), but it cannot protect against a backfill that
 *       does not exist.</li>
 *   <li><b>The target-system problem must be settled.</b> Every change is resolved against a target system
 *       ({@code ChangeProcessStrategyFactory} → {@code TargetSystemManager#getTargetSystem}, which throws
 *       on an unknown id), and there is no default or null-object target system. Audit cleanup operates on
 *       the audit store, not on a target system, so it has no id to supply. The Mongock importer sidesteps
 *       this by taking one from {@code @MongockSupport(targetSystem = ...)}; there is no equivalent here.</li>
 * </ol>
 * When wiring, append a system change to {@code FlamingockAnnotationProcessorPlugin#findAnnotatedChanges()}
 * — mirroring {@code MongockAnnotationProcessorPlugin#getImporterChange()} — with
 * {@code setSystem(true)} and an {@code order} after the backfill's. Reserved sequence: Mongock import
 * {@code 00100}, journal backfill {@code 00200}, audit cleanup {@code 00300}. Those orders are compared as
 * plain strings and nothing enforces uniqueness, so the backfill's author has to be told which value is
 * taken.
 *
 * <h2>Why it fails instead of skipping when the journal is off</h2>
 * With {@code Features.JOURNAL_EVENTS} disabled no journal event has ever been written, so the audit ledger
 * is the only copy of the history and compacting it would be destructive. Failing is therefore the correct
 * outcome rather than an inconvenience: the change is recorded as failed, is retried on the next run, and
 * can never compact a store whose history is not preserved elsewhere.
 * <p>
 * Skipping silently was rejected: a skipped-but-recorded change would be marked applied without doing
 * anything, and so would never run again once the flag was finally enabled — leaving the audit store
 * uncompacted forever, which is the failure mode hardest to notice.
 */
public class AuditCleanupChange {

    private static final Logger logger = FlamingockLoggerFactory.getLogger("AuditCleanup");

    /**
     * Compacts the audit store, failing loudly if that would be unsafe or if it does not fully succeed.
     * <p>
     * Deliberately the only method on this class. Apply-method resolution short-circuits on a unique method
     * name ({@code ReflectionUtil#getDeclaredMethodFromParameterTypeNames} returns immediately when exactly
     * one declared method matches by name), so keeping this the sole method means the declared parameter
     * types in the change's {@code PreviewMethod} never have to be kept in sync with this signature. Adding
     * an overload would quietly move resolution onto the exact-type-matching path.
     *
     * @param auditCompactor the audit store's compaction capability, resolved from the runtime context
     * @throws FlamingockException if journal events are disabled, or if compaction reports a failure
     */
    public void compactAuditStore(@NonLockGuarded AuditCompactor auditCompactor) {
        if (FeatureFlag.isDisabled(Features.JOURNAL_EVENTS)) {
            throw new FlamingockException("Audit cleanup requires journal events to be enabled."
                    + " With the journal disabled, nothing has preserved this store's change history, so"
                    + " compacting the audit store would delete the only copy of it. Enable"
                    + " journal events and let the journal backfill run before compacting.");
        }

        logger.info("Compacting the audit store to one record per change");
        Result result = auditCompactor.compact();

        if (result.isError()) {
            // The capability reports; this change decides. Letting an error pass would record the change as
            // applied over a store that was never compacted - which surfaces far later, for instance as
            // SQL's "Current audit state update matched 2 rows" on the next ordinary write.
            throw new FlamingockException("Audit cleanup failed", ((Result.Error) result).getError());
        }

        logger.info("Audit store compacted");
    }
}
