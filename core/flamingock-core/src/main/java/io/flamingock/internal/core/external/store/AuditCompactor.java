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

import io.flamingock.internal.util.Result;

/**
 * Collapses an audit store's records to one per change — the change's current effective state.
 * <p>
 * A maintenance face, deliberately separate from the operational {@code AuditWriter}/{@code AuditReader}
 * pair. It exists because the audit store changed responsibility: it used to be an append-oriented ledger
 * holding one record per state transition, and it now holds only the current state of each change, with the
 * history moved to the journal (see {@code docs/ADR-0001}). Compaction is the one-off migration between
 * those two shapes, driven by a system change that runs after the journal backfill has preserved the
 * history as events.
 * <p>
 * Note that this is an optimization, not a correctness fix. ADR-0001 is explicit that state reconstruction
 * keeps working against legacy multi-record changes and that no migration is needed merely to rewrite them.
 * What compaction buys is a bounded table, the ability to later enforce one record per change at the
 * database level, and a path to a reader that no longer has to aggregate.
 * <p>
 * Named in the current {@code Audit*} vocabulary. It will be renamed along with everything else when
 * {@code Audit} becomes {@code ChangeState} (see {@code CHANGE_STATE_JOURNAL_WRITE_DESIGN} section 8,
 * step 1) — pre-empting that here would produce the only {@code ChangeState*} type in the codebase and a
 * file the mechanical rename sweep would skip.
 * <p>
 * Single abstract method on purpose, so trivial implementations can be lambdas.
 */
public interface AuditCompactor {

    /**
     * Collapses the store's audit records so that each change id retains exactly its current effective
     * state, and returns the outcome.
     * <p>
     * Implementations must honor the following. Several of these are not stylistic: breaking them either
     * changes which changes Flamingock believes are applied, or loses a change's record entirely.
     *
     * <ol>
     *   <li><b>Semantically a no-op for the operational path.</b> After this call,
     *       {@code AuditReader.getAuditSnapshotByChangeId()} must map exactly the same change ids to
     *       exactly the same effective entries as before it. This is the load-bearing invariant:
     *       {@code CommunityExecutionPlanner} is the only operational consumer of the audit store and it
     *       reads that map, and {@code getAuditIssues()} derives from the same snapshot. Compaction changes
     *       how state is stored, never what the state is.</li>
     *
     *   <li><b>The surviving record is not the implementation's choice.</b> It must be the record
     *       {@code AuditSnapshotBuilder} would select, i.e. {@code AuditEntry.getMostRelevant} /
     *       {@code AuditEntry.shouldBeReplacedBy}: the later {@code createdAt} wins, and on exactly equal
     *       {@code createdAt} the higher {@link io.flamingock.internal.common.core.audit.AuditEntry.Status}
     *       priority wins. An implementation that selects server-side must transcribe that rule faithfully
     *       — ordering by {@code createdAt} descending and taking the first row is <em>not</em> conformant,
     *       because it resolves the equal-timestamp case arbitrarily.</li>
     *
     *   <li><b>At most one stored record per change id afterwards.</b> Keying on change id alone is sound
     *       because {@code LoadedPipeline.validate()} rejects duplicate change ids across all stages.</li>
     *
     *   <li><b>Total, never pipeline-scoped.</b> Every change id present in the store is compacted,
     *       including changes no longer declared in any stage and entries with {@code systemChange} set.
     *       Implementations must not consult the pipeline: dropping a change that is no longer declared
     *       would permanently lose the record that it was applied.</li>
     *
     *   <li><b>Nothing is lost beyond the superseded records.</b> The surviving record's field values are
     *       preserved verbatim — no re-derivation, no re-stamping of {@code createdAt}, no normalizing of
     *       {@code executionId}.</li>
     *
     *   <li><b>Idempotent.</b> Calling this twice must have the same observable effect as calling it once.
     *       It must be safe on an already-compacted store, on an empty store, and as a retry after a
     *       previous call that completed only partially.</li>
     *
     *   <li><b>Not required to be atomic.</b> No store can be: DynamoDB caps a transaction at 100 items
     *       and a batch write at 25 (and the latter is not atomic), Couchbase transactions have no upsert,
     *       and the SQL store spans eleven dialects. A crash part-way through is a legal intermediate
     *       state, which is why the previous point matters.</li>
     *
     *   <li><b>Where the physical key changes, write the survivor before deleting anything.</b> In stores
     *       whose record key differs between the two shapes — DynamoDB
     *       ({@code executionId#changeId#state} becomes {@code changeId}) and Couchbase (same document-key
     *       shape) — compaction is a rekey, not a delete. The survivor must be written under its new key
     *       <em>before</em> any old-keyed record for that change is removed, so that at every instant every
     *       change id that had a record still has one. Violating this ordering can leave a change with no
     *       record at all, and that is unrecoverable: the planner would treat the change as never applied
     *       and re-execute it against the target system. Leaving duplicates behind, by contrast, is
     *       recoverable — the selection rule still yields the correct snapshot and the next call converges.
     *       MongoDB and SQL do not rekey, so for them compaction is simply removing the superseded
     *       records.</li>
     *
     *   <li><b>Indexes and schema are out of scope.</b> Implementations must not create, drop or alter any
     *       index, constraint, table or collection. In particular MongoDB's unique index on
     *       {@code (executionId, changeId, state)} stays in place. Enforcing one record per change at the
     *       database level is a separate, later concern: compaction has to stay repeatable and cheap, and
     *       must not be coupled to a schema migration with a different failure story.</li>
     *
     *   <li><b>No journal events.</b> Compaction changes storage layout, not change state, so it must not
     *       append {@code JournalEvent}s and must bypass the {@code AuditWriter#writeEntry} path that the
     *       persistence takes while {@code Features.JOURNAL_EVENTS} is enabled. Emitting events here would
     *       corrupt the very ledger the journal backfill just produced.</li>
     *
     *   <li><b>The normal write path must still work afterwards.</b> An ordinary current-state write for a
     *       change id that already existed must succeed once compaction has run. This is not a tautology:
     *       the SQL store's current-state write updates by change id and fails when it matches more than
     *       one row, so a compaction that leaves two rows behind breaks the <em>next</em> run rather than
     *       its own.</li>
     *
     *   <li><b>The caller guarantees exclusivity.</b> This runs as a system change under the Flamingock
     *       lock, so a single writer may be assumed. Implementations are not required to be correct against
     *       a concurrent ordinary execution.</li>
     * </ol>
     *
     * <p>
     * Failures are reported, not thrown: infrastructure errors come back as {@code Result.Error} carrying
     * the cause, matching {@code AuditWriter#writeEntry}, the sibling with the same return type. Reporting
     * rather than throwing is what lets the calling system change decide — a failed compaction is not
     * itself harmful, since the store is still correct, merely not compacted. Ordering guarantee 8 still
     * holds on the error path. The caller must not silently discard an error: a system change that ignored
     * it would record itself as applied over a store that was never compacted.
     * <p>
     * {@code Result} carries no counts today. That was deliberate rather than an oversight: DynamoDB's
     * audit table is partition-key-only and can only be scanned, so "records removed" is not obtainable
     * comparably across the supported stores. The return type can be widened later without breaking
     * callers that only inspect {@code isError()}.
     *
     * @return {@link Result#OK()} once the store holds at most one record per change id, or
     *         {@code Result.Error} carrying the cause if compaction could not complete
     */
    Result compact();
}
