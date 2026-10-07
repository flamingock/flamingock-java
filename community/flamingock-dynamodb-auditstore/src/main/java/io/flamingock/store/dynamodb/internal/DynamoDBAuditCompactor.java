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
package io.flamingock.store.dynamodb.internal;

import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.audit.AuditSnapshotBuilder;
import io.flamingock.internal.core.external.store.AuditCompactor;
import io.flamingock.internal.util.Result;
import io.flamingock.internal.util.dynamodb.entities.AuditEntryEntity;
import io.flamingock.internal.util.log.FlamingockLoggerFactory;
import org.slf4j.Logger;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.ScanEnhancedRequest;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Collapses the DynamoDB audit table to one record per change.
 * <p>
 * DynamoDB is the awkward case among the audit stores, for one reason: the audit table is
 * partition-key-only, and the two record shapes use <em>different</em> keys. The historical ledger keys a
 * record by {@code executionId#changeId#state} (see {@link AuditEntryEntity#partitionKey}); the
 * current-state form keys it by the bare {@code changeId} (what
 * {@link DynamoDBAuditRepository#contributeToTransaction} writes). So compaction here is a <b>rekey</b>, not
 * merely a deletion of superseded records — and because there is no sort key and no secondary index, the
 * records of a change cannot be located at all without a full table scan.
 * <p>
 * Nothing is lost by rekeying: {@link AuditEntryEntity#toAuditEntry()} reads every field from its own
 * attribute and never parses the partition key, so the key carries no information of its own.
 *
 * <h2>Why this is not atomic, and why that is fine</h2>
 * It cannot be: a DynamoDB transaction caps at 100 items and a batch write at 25, so a real ledger does not
 * fit in one atomic operation. What the contract requires instead is convergence, and that comes from a
 * strict ordering — <b>the survivor is written under its new key before any old-keyed record is deleted</b>.
 * A crash therefore leaves duplicates, which the next run collapses because the selection rule still yields
 * the same winner; it can never leave a change with no record at all, which would be unrecoverable since
 * the planner would treat the change as never applied and re-execute it against the target system.
 * <p>
 * Changes are compacted independently and the first failure aborts the rest, so a partial run always leaves
 * a state the next run can finish.
 */
class DynamoDBAuditCompactor implements AuditCompactor {

    private static final Logger logger = FlamingockLoggerFactory.getLogger("DynamoDBAuditCompactor");

    private final DynamoDbTable<AuditEntryEntity> table;

    DynamoDBAuditCompactor(DynamoDbTable<AuditEntryEntity> table) {
        this.table = Objects.requireNonNull(table, "table must not be null");
    }

    @Override
    public Result compact() {
        try {
            // The scan is fully drained before anything is written, and that ordering is mandatory, not
            // incidental: DynamoDB scans offer no snapshot isolation across pages, so compacting inside the
            // scan loop would make later pages observe this run's own puts and deletes.
            Map<String, List<AuditEntryEntity>> recordsByChange = scanGroupedByChangeId();
            // Every change id is also a current-state key. Captured so that no change can delete a key that
            // another change legitimately owns - see compactChange.
            Set<String> currentStateKeys = new HashSet<>(recordsByChange.keySet());
            int compacted = 0;
            for (Map.Entry<String, List<AuditEntryEntity>> group : recordsByChange.entrySet()) {
                if (compactChange(group.getKey(), group.getValue(), currentStateKeys)) {
                    compacted++;
                }
            }
            logger.debug("Audit compaction finished [changes={} compacted={}]", recordsByChange.size(), compacted);
            return Result.OK();
        } catch (RuntimeException exception) {
            // Reported rather than thrown, so the calling system change decides. The survivor-before-delete
            // ordering holds on this path too: whatever failed, no change has been left without a record.
            logger.warn("Audit compaction failed", exception);
            return new Result.Error(exception);
        }
    }

    /**
     * Reads the whole table once and groups by change id.
     * <p>
     * {@code items()} walks <b>every</b> page. Do not replace it with the single-page idiom used by
     * {@code DynamoDBJournalEventStore.getUnacknowledgedEvents} ({@code pages.iterator().next()}), which is
     * correct there only because that query carries a limit: here it would silently leave every change
     * beyond the first page uncompacted, and report success.
     * <p>
     * The read must be strongly consistent. An eventually consistent scan could miss a change's most recent
     * record, pick an earlier one as the current state and delete the record it missed — and until the next
     * run the audit store would then report a state the change had already moved past.
     */
    private Map<String, List<AuditEntryEntity>> scanGroupedByChangeId() {
        Map<String, List<AuditEntryEntity>> recordsByChange = new LinkedHashMap<>();
        for (AuditEntryEntity record : table.scan(consistentScan()).items()) {
            String changeId = record.getChangeId();
            if (changeId == null || changeId.trim().isEmpty()) {
                // Such a record is already invisible to the operational path, which looks changes up by id,
                // so it cannot be compacted into anything meaningful. Failing is deliberate: deleting it
                // would be data loss, skipping it would hide corruption, and compaction is exactly when an
                // operator should find out it exists.
                throw new IllegalStateException("Cannot compact the audit store: the record with partitionKey '"
                        + record.getPartitionKey() + "' has no changeId");
            }
            List<AuditEntryEntity> records = recordsByChange.get(changeId);
            if (records == null) {
                records = new ArrayList<>();
                recordsByChange.put(changeId, records);
            }
            records.add(record);
        }
        return recordsByChange;
    }

    static ScanEnhancedRequest consistentScan() {
        return ScanEnhancedRequest.builder().consistentRead(true).build();
    }

    /**
     * @return {@code true} when the change needed work, {@code false} when it was already in current-state form
     */
    private boolean compactChange(String changeId,
                                  List<AuditEntryEntity> records,
                                  Set<String> currentStateKeys) {
        if (records.size() == 1 && changeId.equals(records.get(0).getPartitionKey())) {
            return false;
        }

        AuditEntryEntity survivor = selectSurvivor(changeId, records);

        // Collect the keys to remove BEFORE rekeying the survivor. The survivor is one of these records and
        // is mutated in place, so reading its key after the fact would report the new key and silently spare
        // its own old row. That row would then never be deleted, and a later run could find two records whose
        // keys both already equal the changeId and so delete neither — leaving the change permanently
        // duplicated instead of converging.
        List<String> supersededKeys = new ArrayList<>(records.size());
        for (AuditEntryEntity record : records) {
            String key = record.getPartitionKey();
            if (changeId.equals(key)) {
                continue;
            }
            // A change id may contain '#', so a ledger key of this change can in principle be character-for
            // character another change's id - and therefore that change's current-state key. Deleting it
            // would destroy the only record of a different change, which no re-run could repair. Leaving it
            // is harmless: the change that owns it writes it as its own survivor.
            if (currentStateKeys.contains(key)) {
                logger.warn("Not deleting partitionKey '{}' while compacting changeId '{}': it is also the "
                        + "current-state key of another change", key, changeId);
                continue;
            }
            supersededKeys.add(key);
        }

        survivor.setPartitionKey(changeId);
        // Always before the deletes below. PutItem fully replaces the item, so the rewritten record carries
        // every attribute the stored one had. (Not quite byte-identical: AuditEntryEntity.getTxType()
        // renders a missing txStrategy as NON_TX, so a legacy record lacking it gains that value. Harmless,
        // since AuditEntry's constructor applies the same default, so the snapshot is unchanged.)
        table.putItem(survivor);

        for (String supersededKey : supersededKeys) {
            table.deleteItem(Key.builder().partitionValue(supersededKey).build());
        }
        logger.debug("Compacted change [changeId={} records={} survivingState={}]",
                changeId, records.size(), survivor.getState());
        return true;
    }

    /**
     * Picks the record holding the change's current effective state.
     * <p>
     * Delegates the decision to {@link AuditSnapshotBuilder} rather than reimplementing it, which is what
     * guarantees the audit snapshot is identical before and after compaction. Reimplementing it — for
     * instance as "the latest {@code createdAt}" — would resolve the equal-timestamp case arbitrarily
     * instead of by status priority, and so could silently change which changes are considered applied.
     * <p>
     * The builder works in terms of {@link AuditEntry}, but what has to be written back is the stored
     * <em>entity</em>. Rebuilding one from the converted {@code AuditEntry} would launder the record through
     * two lossy conversions: it would reset {@code partitionKey} to the ledger key, and it would materialise
     * defaults for attributes the stored record simply did not have ({@code txStrategy} becomes
     * {@code NON_TX}, {@code recoveryStrategy} becomes {@code MANUAL_INTERVENTION}). So the converted entries
     * are tracked back to their originals by identity — sound because {@code AuditSnapshotBuilder} returns
     * the very instances it was given, and {@code AuditEntry} declares no {@code equals}.
     * <p>
     * A single-record group is short-circuited: there is nothing to decide, and converting is the one step
     * that can fail on a legacy record missing {@code state}, {@code type}, {@code executionMillis} or
     * {@code systemChange}, none of which {@link AuditEntryEntity#toAuditEntry()} tolerates being absent.
     */
    private AuditEntryEntity selectSurvivor(String changeId, List<AuditEntryEntity> records) {
        if (records.size() == 1) {
            return records.get(0);
        }
        Map<AuditEntry, AuditEntryEntity> originalOf = new IdentityHashMap<>();
        AuditSnapshotBuilder builder = new AuditSnapshotBuilder();
        for (AuditEntryEntity record : records) {
            AuditEntry converted = record.toAuditEntry();
            originalOf.put(converted, record);
            builder.addEntry(converted);
        }

        List<AuditEntry> survivors = builder.buildList();
        if (survivors.size() != 1) {
            throw new IllegalStateException("Expected a single effective state for changeId '" + changeId
                    + "' but the snapshot produced " + survivors.size());
        }
        AuditEntryEntity survivor = originalOf.get(survivors.get(0));
        if (survivor == null) {
            throw new IllegalStateException("Could not map the effective state of changeId '" + changeId
                    + "' back to its stored record");
        }
        return survivor;
    }
}
