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
package io.flamingock.store.sql.internal;

import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.audit.AuditSnapshotBuilder;
import io.flamingock.internal.common.core.external.ExecutionWrapper;
import io.flamingock.internal.core.context.BasicRuntimeContext;
import io.flamingock.internal.core.external.store.AuditCompactor;
import io.flamingock.internal.util.Result;
import io.flamingock.internal.util.log.FlamingockLoggerFactory;
import org.slf4j.Logger;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Collapses the SQL audit table to one record per change.
 * <p>
 * SQL is the simple case among the audit stores: the current-state row and the ledger rows share the
 * same key shape, filtered only by {@code change_id}, so there is no rekey to perform (contrast DynamoDB
 * and Couchbase, which key the two shapes differently). Compacting a change is therefore just deleting
 * every row for it and inserting the survivor fresh, which {@link SqlAuditRepository#replaceForCompaction}
 * does on a connection this class wraps in a transaction via {@link ExecutionWrapper}.
 * <p>
 * That transaction is what satisfies the "never leave a change with zero records" invariant here: the
 * delete and the insert either both land or neither does, so a crash mid-compaction rolls back to the
 * pre-compaction state rather than leaving a gap. {@code ExecutionWrapper} is the same transactional
 * machinery the ordinary journal-enabled write path already uses (see
 * {@code SqlAuditPersistence#writeEntry}), proven across all eleven supported dialects.
 * <p>
 * Changes are compacted independently and the first failure aborts the rest, so a partial run always
 * leaves a state the next run can finish.
 */
class SqlAuditCompactor implements AuditCompactor {

    private static final Logger logger = FlamingockLoggerFactory.getLogger("SqlAuditCompactor");

    private final SqlAuditRepository auditRepository;
    private final ExecutionWrapper txWrapper;

    SqlAuditCompactor(SqlAuditRepository auditRepository, ExecutionWrapper txWrapper) {
        this.auditRepository = Objects.requireNonNull(auditRepository, "auditRepository must not be null");
        this.txWrapper = Objects.requireNonNull(txWrapper, "txWrapper must not be null");
    }

    @Override
    public Result compact() {
        try {
            Map<String, List<AuditEntry>> entriesByChange = groupByChangeId(auditRepository.getAuditHistory());
            Map<String, AuditEntry> survivors = selectSurvivors(entriesByChange);
            int compacted = 0;
            for (Map.Entry<String, List<AuditEntry>> group : entriesByChange.entrySet()) {
                if (compactChange(group.getKey(), group.getValue().size(), survivors.get(group.getKey()))) {
                    compacted++;
                }
            }
            logger.debug("Audit compaction finished [changes={} compacted={}]", entriesByChange.size(), compacted);
            return Result.OK();
        } catch (RuntimeException exception) {
            // Reported rather than thrown, so the calling system change decides. Fail-fast: whatever
            // change failed, every change compacted before it stays compacted (atomic per change), and
            // every change after it is simply untouched - both are safe to retry on the next run.
            logger.warn("Audit compaction failed", exception);
            return new Result.Error(exception);
        }
    }

    /**
     * Reads the whole table once and groups by change id.
     * <p>
     * {@link SqlAuditRepository#getAuditHistory()} already reads unbounded and unfiltered, so every
     * change id present - including ones no longer declared in any pipeline stage, and system-change
     * entries - is captured here too.
     */
    private Map<String, List<AuditEntry>> groupByChangeId(List<AuditEntry> history) {
        Map<String, List<AuditEntry>> entriesByChange = new LinkedHashMap<>();
        for (AuditEntry entry : history) {
            String changeId = entry.getChangeId();
            if (changeId == null || changeId.trim().isEmpty()) {
                // Such a record is already invisible to the operational path, which looks changes up by
                // id, so it cannot be compacted into anything meaningful. Failing is deliberate: deleting
                // it would be data loss, skipping it would hide corruption, and compaction is exactly when
                // an operator should find out it exists.
                throw new IllegalStateException("Cannot compact the audit store: a record has no changeId");
            }
            entriesByChange.computeIfAbsent(changeId, key -> new ArrayList<>()).add(entry);
        }
        return entriesByChange;
    }

    /**
     * Picks, for every change in one pass, the record holding its current effective state.
     * <p>
     * Delegates the decision to {@link AuditSnapshotBuilder} rather than reimplementing it, which is what
     * guarantees the audit snapshot is identical before and after compaction (contract clauses 1 and 2).
     * Reimplementing it - for instance as "the latest {@code createdAt}" - would resolve the
     * equal-timestamp case arbitrarily instead of by status priority, and so could silently change which
     * changes are considered applied.
     * <p>
     * One builder for the whole table rather than one per change: {@code AuditSnapshotBuilder} already
     * groups by change id internally, so feeding it the full history directly reuses that grouping instead
     * of re-deriving it per change. Unlike DynamoDB's and Couchbase's compactors, no identity map back to a
     * stored entity is needed either: SQL has no rekey, so each survivor {@link AuditEntry} here is exactly
     * what gets re-inserted.
     */
    private Map<String, AuditEntry> selectSurvivors(Map<String, List<AuditEntry>> entriesByChange) {
        AuditSnapshotBuilder builder = new AuditSnapshotBuilder();
        for (List<AuditEntry> records : entriesByChange.values()) {
            for (AuditEntry record : records) {
                builder.addEntry(record);
            }
        }
        return builder.buildMap();
    }

    /**
     * @return {@code true} when the change needed work, {@code false} when it was already in
     * current-state form
     */
    private boolean compactChange(String changeId, int recordCount, AuditEntry survivor) {
        if (recordCount == 1) {
            return false;
        }

        BasicRuntimeContext runtimeContext = new BasicRuntimeContext("compact-" + changeId);
        Result result = txWrapper.wrapExecution(runtimeContext, ctx -> {
            Connection connection = ctx.getContext().getRequiredDependencyValue(Connection.class);
            return auditRepository.replaceForCompaction(connection, changeId, survivor);
        });
        if (result.isError()) {
            throw new IllegalStateException("Compaction of changeId '" + changeId + "' failed",
                    ((Result.Error) result).getError());
        }

        logger.debug("Compacted change [changeId={} records={} survivingState={}]",
                changeId, recordCount, survivor.getState());
        return true;
    }
}
