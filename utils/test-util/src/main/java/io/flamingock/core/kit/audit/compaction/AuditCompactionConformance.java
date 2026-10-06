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
package io.flamingock.core.kit.audit.compaction;

import io.flamingock.core.kit.audit.AuditEntryTestFactory;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.feature.Features;
import io.flamingock.internal.util.FeatureFlag;
import io.flamingock.internal.util.Result;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The shared contract test for {@code AuditCompactor}: one set of properties every audit store must
 * satisfy, so each store proves conformance instead of hand-rolling its own idea of what compaction means.
 * <p>
 * Deliberately <em>not</em> a JUnit base class. The SQL store's suite is parameterized over dialects and
 * builds its container inside the test method, and JUnit cannot promote an inherited {@code @Test} into a
 * {@code @ParameterizedTest} — so a base class could not serve the one store that needs sharing most. As
 * plain methods, the four NoSQL stores can call one property per {@code @Test} for granular reporting while
 * SQL calls {@link #verifyAll()} once per dialect, avoiding a fresh Oracle container per property. For the
 * same reason this class depends on no test framework and signals failure with {@link AssertionError}.
 * <p>
 * Each property resets the store, seeds, <em>checks the seed actually landed</em>, then compacts and
 * asserts. The seed check is not padding: three of the five stores make
 * {@code (executionId, changeId, state)} their physical key and silently overwrite a repeat, so a seeding
 * mistake would otherwise surface as a bogus compaction failure.
 * <p>
 * Seeding rule, consequently: never two records with the same {@code (executionId, changeId, state)}.
 * MongoDB's unique index rejects it, DynamoDB and Couchbase silently overwrite, and SQL — which has no such
 * constraint — would keep both and diverge from the others. Realistic ledgers do not need it anyway: a
 * change accumulates records by changing {@code state} within an execution and by being retried under a new
 * {@code executionId}.
 * <p>
 * All timestamps are whole seconds, because SQL column types truncate sub-second precision differently per
 * dialect and a truncated tie would silently turn an ordering assertion into a priority assertion.
 */
public final class AuditCompactionConformance {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 1, 1, 12, 0, 0);
    private static final LocalDateTime T1 = LocalDateTime.of(2026, 1, 2, 12, 0, 0);
    private static final LocalDateTime T2 = LocalDateTime.of(2026, 1, 3, 12, 0, 0);
    private static final LocalDateTime T3 = LocalDateTime.of(2026, 1, 4, 12, 0, 0);

    private static final String CHANGE_A = "compaction-change-a";
    private static final String CHANGE_B = "compaction-change-b";
    private static final String CHANGE_C = "compaction-change-c";
    private static final String CHANGE_D = "compaction-change-d";
    private static final String CHANGE_E = "compaction-change-e";
    private static final String CHANGE_F = "compaction-change-f";

    private final AuditCompactionFixture fixture;

    public AuditCompactionConformance(AuditCompactionFixture fixture) {
        this.fixture = Objects.requireNonNull(fixture, "fixture must not be null");
    }

    /**
     * Runs every property that is cheap enough to be mandatory, resetting between each.
     * <p>
     * Excludes {@link #verifyLargeLedgerIsCompacted(int, int)}, which is opt-in — see its javadoc.
     */
    public void verifyAll() {
        verifySeedingRoundTripsFaithfully();
        verifySnapshotPreserved();
        verifyOneRecordPerChange();
        verifyIsFixpoint();
        verifySubsequentCurrentStateWriteSucceeds();
        verifyUnknownChangesPreserved();
        verifyAlreadySingleRecordUntouched();
        verifyEmptyStoreIsNoOp();
        verifyTimestampTieBreaksOnStatusPriority();
        verifySystemChangeEntriesPreserved();
    }

    // ---------------------------------------------------------------------------------------------
    // Properties
    // ---------------------------------------------------------------------------------------------

    /**
     * Not a compaction property — a guard on the suite itself.
     * <p>
     * Everything below assumes the store gives back what was seeded. If a SQL dialect's timestamp column
     * truncates below whole-second precision, or a key-shaped store overwrites a record the suite believed
     * was distinct, later properties would still pass or fail but would no longer be testing what they
     * claim. Checking it once, first, turns that into an honest failure here.
     * <p>
     * Only the fields the later properties depend on are compared \u2014 the identity triple, {@code createdAt}
     * and {@code systemChange} \u2014 rather than every field. This is the one comparison that crosses a
     * store's serialization boundary, and how faithfully a store's mapper round-trips every last field is a
     * different question from whether compaction is correct. Asserting all of them here would fail stores
     * over pre-existing mapper quirks that no property relies on.
     */
    public void verifySeedingRoundTripsFaithfully() {
        fixture.reset();
        AuditEntry seeded = entry("exec-roundtrip", CHANGE_A, AuditEntry.Status.APPLIED, T2);
        fixture.seedLedgerEntry(seeded);

        List<AuditEntry> stored = fixture.readStoredRecords();
        if (stored.size() != 1) {
            throw new AssertionError("Seeding one record stored " + stored.size()
                    + ". The suite cannot verify compaction against a store that does not round-trip seeds.");
        }
        assertSeedIdentityPreserved(seeded, stored.get(0));
    }

    /**
     * Compaction must not change what the operational path sees.
     * <p>
     * The planner reads {@code getAuditSnapshotByChangeId()} to decide what still needs executing, so this
     * is the property that makes compaction safe at all: an implementation that picked a different survivor
     * would silently change which changes are considered applied.
     */
    public void verifySnapshotPreserved() {
        seedCanonicalLedger();

        Map<String, AuditEntry> before = fixture.auditReader().getAuditSnapshotByChangeId();
        requireOk(fixture.compact(), "compact");
        Map<String, AuditEntry> after = fixture.auditReader().getAuditSnapshotByChangeId();

        if (!before.keySet().equals(after.keySet())) {
            throw new AssertionError("Snapshot change ids differ after compaction. Before=" + before.keySet()
                    + " after=" + after.keySet());
        }
        for (Map.Entry<String, AuditEntry> expected : before.entrySet()) {
            assertFieldsPreserved(expected.getValue(), after.get(expected.getKey()),
                    "snapshot entry for '" + expected.getKey() + "'");
        }
    }

    /**
     * The post-condition: one stored record per change, and specifically the right one.
     * <p>
     * Asserted against the physical records rather than the snapshot, because the snapshot aggregates and
     * so cannot distinguish a compacted store from an untouched one.
     */
    public void verifyOneRecordPerChange() {
        Map<String, AuditEntry.Status> expectedWinners = seedCanonicalLedger();

        requireOk(fixture.compact(), "compact");

        Map<String, List<AuditEntry>> byChange = groupByChangeId(fixture.readStoredRecords());
        for (Map.Entry<String, List<AuditEntry>> group : byChange.entrySet()) {
            if (group.getValue().size() != 1) {
                throw new AssertionError("Change '" + group.getKey() + "' retained "
                        + group.getValue().size() + " records after compaction, expected 1: "
                        + describe(group.getValue()));
            }
        }
        if (!byChange.keySet().equals(expectedWinners.keySet())) {
            throw new AssertionError("Change ids present after compaction differ from those seeded. Expected="
                    + expectedWinners.keySet() + " actual=" + byChange.keySet());
        }
        for (Map.Entry<String, AuditEntry.Status> expected : expectedWinners.entrySet()) {
            AuditEntry survivor = byChange.get(expected.getKey()).get(0);
            if (survivor.getState() != expected.getValue()) {
                throw new AssertionError("Wrong survivor for '" + expected.getKey() + "': expected state "
                        + expected.getValue() + " but kept " + survivor.getState()
                        + ". The survivor must be the one AuditSnapshotBuilder selects.");
            }
        }
    }

    /**
     * Compaction must be idempotent, because no store can make it atomic and a retry after a partial run has
     * to converge rather than compound.
     * <p>
     * Also compacts a third time, on the already-single-record store. That is the branch with no superseded
     * records to remove, which a rekeying implementation can get wrong by re-writing the survivor and then
     * deleting what it just wrote.
     */
    public void verifyIsFixpoint() {
        seedCanonicalLedger();

        requireOk(fixture.compact(), "first compact");
        List<AuditEntry> afterFirst = sortedByChangeId(fixture.readStoredRecords());
        Map<String, AuditEntry> snapshotAfterFirst = fixture.auditReader().getAuditSnapshotByChangeId();

        requireOk(fixture.compact(), "second compact");
        assertSameRecords(afterFirst, sortedByChangeId(fixture.readStoredRecords()), "second compact");
        if (!snapshotAfterFirst.keySet().equals(
                fixture.auditReader().getAuditSnapshotByChangeId().keySet())) {
            throw new AssertionError("Second compaction changed the snapshot; compaction is not idempotent");
        }

        requireOk(fixture.compact(), "third compact");
        assertSameRecords(afterFirst, sortedByChangeId(fixture.readStoredRecords()), "third compact");
    }

    /**
     * After compaction the store's ordinary current-state write must still work for a change that already
     * existed.
     * <p>
     * The sharpest property in the suite, and the reason it is worth sharing. A SQL compaction that leaves
     * two rows behind passes everything that only inspects the snapshot, then breaks the <em>next</em> run,
     * because the current-state update fails when it matches more than one row. A DynamoDB or Couchbase
     * compaction that collapses records without rekeying also passes a naive row count, then puts the next
     * write under {@code changeId} while the survivor still sits under
     * {@code executionId#changeId#state} — two records again.
     * <p>
     * Requires {@code JOURNAL_EVENTS} to be enabled, since that is what selects the current-state write
     * path; with the flag off, {@code writeEntry} appends and would prove nothing.
     */
    public void verifySubsequentCurrentStateWriteSucceeds() {
        seedCanonicalLedger();
        requireOk(fixture.compact(), "compact");

        boolean journalEventsWereEnabled = FeatureFlag.isEnabled(Features.JOURNAL_EVENTS);
        FeatureFlag.enable(Features.JOURNAL_EVENTS);
        try {
            AuditEntry followUp = entry("exec-after-compaction", CHANGE_A, AuditEntry.Status.APPLIED, T3);
            requireOk(fixture.writeCurrentState(followUp), "current-state write after compaction");

            List<AuditEntry> forChangeA = groupByChangeId(fixture.readStoredRecords()).get(CHANGE_A);
            if (forChangeA == null || forChangeA.size() != 1) {
                throw new AssertionError("After compaction, writing the current state of '" + CHANGE_A
                        + "' left " + (forChangeA == null ? 0 : forChangeA.size())
                        + " records, expected 1. Compaction likely left superseded records behind, or"
                        + " collapsed them without rekeying to the current-state key.");
            }
            if (forChangeA.get(0).getState() != AuditEntry.Status.APPLIED) {
                throw new AssertionError("The current-state write did not become the stored state for '"
                        + CHANGE_A + "'; found " + forChangeA.get(0).getState());
            }
        } finally {
            if (journalEventsWereEnabled) {
                FeatureFlag.enable(Features.JOURNAL_EVENTS);
            } else {
                FeatureFlag.remove(Features.JOURNAL_EVENTS);
            }
        }
    }

    /**
     * A change that is no longer declared in any stage must still be compacted and must survive.
     * <p>
     * Implementations must not consult the pipeline: dropping such a change would permanently erase the
     * record that it was applied, and the planner would then re-execute it against the target system.
     */
    public void verifyUnknownChangesPreserved() {
        fixture.reset();
        seed(Arrays.asList(
                entry("exec-1", CHANGE_E, AuditEntry.Status.STARTED, T0),
                entry("exec-1", CHANGE_E, AuditEntry.Status.APPLIED, T0.plusMinutes(1))));

        requireOk(fixture.compact(), "compact");

        List<AuditEntry> stored = fixture.readStoredRecords();
        if (stored.size() != 1 || !CHANGE_E.equals(stored.get(0).getChangeId())) {
            throw new AssertionError("A change absent from the pipeline must be compacted to exactly one"
                    + " surviving record, found " + describe(stored));
        }
        if (stored.get(0).getState() != AuditEntry.Status.APPLIED) {
            throw new AssertionError("Wrong survivor for an undeclared change: " + stored.get(0).getState());
        }
    }

    /**
     * A change that already has a single record must come out byte-identical.
     * <p>
     * Guards the "nothing is lost beyond the superseded records" clause, in the one scenario where the
     * implementation has nothing to decide. A rekeying store in particular must move the record without
     * re-stamping {@code createdAt} or rewriting {@code executionId}.
     */
    public void verifyAlreadySingleRecordUntouched() {
        fixture.reset();
        seed(Arrays.asList(entry("exec-1", CHANGE_C, AuditEntry.Status.APPLIED, T0)));

        // Read the record back BEFORE compacting, so the comparison is store-to-store rather than
        // in-memory-to-store. Whether a store's mapper round-trips every field is a separate question from
        // whether compaction preserved the record, and only the latter is this property's business.
        AuditEntry before = fixture.readStoredRecords().get(0);

        requireOk(fixture.compact(), "compact");

        List<AuditEntry> stored = fixture.readStoredRecords();
        if (stored.size() != 1) {
            throw new AssertionError("Compacting a single-record change produced " + stored.size()
                    + " records, expected 1");
        }
        assertFieldsPreserved(before, stored.get(0), "untouched single record");
    }

    /**
     * Compaction of an empty store succeeds and leaves it empty.
     * <p>
     * Trivial, and worth having: it is where a scan-then-group implementation dereferences nothing, and
     * where a SQL delete driven by a subquery over an empty table misbehaves on several dialects.
     */
    public void verifyEmptyStoreIsNoOp() {
        fixture.reset();

        requireOk(fixture.compact(), "compact on empty store");

        List<AuditEntry> stored = fixture.readStoredRecords();
        if (!stored.isEmpty()) {
            throw new AssertionError("Compacting an empty store produced " + describe(stored));
        }
    }

    /**
     * When two records share an exact {@code createdAt}, status priority decides.
     * <p>
     * Isolated from the other properties because it is the single likeliest divergence for an
     * implementation that selects server-side: ordering by {@code createdAt} descending and taking the
     * first row resolves this case arbitrarily. Failing here points straight at the tie-break rule instead
     * of showing up as an unexplained wrong-survivor error.
     */
    public void verifyTimestampTieBreaksOnStatusPriority() {
        fixture.reset();
        seed(Arrays.asList(
                entry("exec-1", CHANGE_D, AuditEntry.Status.APPLIED, T2),
                entry("exec-1", CHANGE_D, AuditEntry.Status.ROLLED_BACK, T2)));

        requireOk(fixture.compact(), "compact");

        List<AuditEntry> stored = fixture.readStoredRecords();
        if (stored.size() != 1) {
            throw new AssertionError("Expected one surviving record for the tie case, found "
                    + describe(stored));
        }
        if (stored.get(0).getState() != AuditEntry.Status.ROLLED_BACK) {
            throw new AssertionError("With equal createdAt, the higher-priority status must win:"
                    + " expected ROLLED_BACK (priority "
                    + AuditEntry.Status.ROLLED_BACK.getPriority() + ") over APPLIED (priority "
                    + AuditEntry.Status.APPLIED.getPriority() + "), but kept " + stored.get(0).getState()
                    + ". Selecting by createdAt alone is not sufficient.");
        }
    }

    /**
     * System-change entries compact like any other and keep their flag.
     * <p>
     * They matter because the Mongock importer writes them and {@code PipelineHelper} routes on them, and
     * because a boxed {@code Boolean} is easy to lose in a mapper round-trip.
     */
    public void verifySystemChangeEntriesPreserved() {
        fixture.reset();
        seed(Arrays.asList(
                systemEntry("exec-1", CHANGE_F, AuditEntry.Status.STARTED, T0),
                systemEntry("exec-1", CHANGE_F, AuditEntry.Status.APPLIED, T0.plusMinutes(1))));

        requireOk(fixture.compact(), "compact");

        List<AuditEntry> stored = fixture.readStoredRecords();
        if (stored.size() != 1) {
            throw new AssertionError("Expected one surviving system-change record, found "
                    + describe(stored));
        }
        if (!Boolean.TRUE.equals(stored.get(0).getSystemChange())) {
            throw new AssertionError("Compaction lost the systemChange flag; got "
                    + stored.get(0).getSystemChange());
        }
    }

    /**
     * Compacts a ledger large enough to exceed a single batch.
     * <p>
     * Opt-in rather than part of {@link #verifyAll()}: it is the only property that exercises DynamoDB's
     * 25-item batch and 100-item transaction caps, or an {@code IN} list long enough to hit Oracle's
     * expression limit, and it would otherwise multiply the cost of the dialect-parameterized SQL suite for
     * stores that cannot fail that way. Stores with a batching implementation should enable it.
     *
     * @param changes        how many distinct change ids to seed
     * @param statesPerChange how many records per change; must be at most the number of
     *                        {@link AuditEntry.Status} values, since records of one change under one
     *                        execution are made distinct by their state
     */
    public void verifyLargeLedgerIsCompacted(int changes, int statesPerChange) {
        AuditEntry.Status[] states = AuditEntry.Status.values();
        if (statesPerChange < 1 || statesPerChange > states.length) {
            throw new IllegalArgumentException("statesPerChange must be between 1 and " + states.length);
        }
        fixture.reset();

        List<AuditEntry> seeds = new ArrayList<>(changes * statesPerChange);
        for (int change = 0; change < changes; change++) {
            String changeId = "compaction-bulk-" + change;
            for (int state = 0; state < statesPerChange; state++) {
                seeds.add(entry("exec-bulk", changeId, states[state], T0.plusSeconds(state)));
            }
        }
        seed(seeds);

        requireOk(fixture.compact(), "compact large ledger");

        Map<String, List<AuditEntry>> byChange = groupByChangeId(fixture.readStoredRecords());
        if (byChange.size() != changes) {
            throw new AssertionError("Expected " + changes + " changes after compacting "
                    + seeds.size() + " records, found " + byChange.size()
                    + ". A partially-applied batch is the usual cause.");
        }
        for (Map.Entry<String, List<AuditEntry>> group : byChange.entrySet()) {
            if (group.getValue().size() != 1) {
                throw new AssertionError("Change '" + group.getKey() + "' retained "
                        + group.getValue().size() + " records after compacting a large ledger");
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Seeding
    // ---------------------------------------------------------------------------------------------

    /**
     * Seeds the shared scenario and returns the state each change must be left holding.
     * <p>
     * Six changes covering the cases that distinguish a correct implementation: several transitions within
     * one execution, a retry under a second execution, a change that is already single, an exact timestamp
     * tie, a change absent from the pipeline, and a system change. No two records share
     * {@code (executionId, changeId, state)}.
     */
    private Map<String, AuditEntry.Status> seedCanonicalLedger() {
        fixture.reset();
        seed(Arrays.asList(
                // change-A: one execution, start -> fail -> rolled back
                entry("exec-1", CHANGE_A, AuditEntry.Status.STARTED, T0),
                entry("exec-1", CHANGE_A, AuditEntry.Status.FAILED, T0.plusMinutes(1)),
                entry("exec-1", CHANGE_A, AuditEntry.Status.ROLLED_BACK, T0.plusMinutes(2)),
                // change-B: failed once, retried under a second execution and applied
                entry("exec-1", CHANGE_B, AuditEntry.Status.STARTED, T0),
                entry("exec-1", CHANGE_B, AuditEntry.Status.FAILED, T0.plusMinutes(1)),
                entry("exec-2", CHANGE_B, AuditEntry.Status.STARTED, T1),
                entry("exec-2", CHANGE_B, AuditEntry.Status.APPLIED, T1.plusMinutes(1)),
                // change-C: already a single record
                entry("exec-1", CHANGE_C, AuditEntry.Status.APPLIED, T0),
                // change-D: identical createdAt, decided by status priority
                entry("exec-1", CHANGE_D, AuditEntry.Status.APPLIED, T2),
                entry("exec-1", CHANGE_D, AuditEntry.Status.ROLLED_BACK, T2),
                // change-E: no longer declared in any stage
                entry("exec-1", CHANGE_E, AuditEntry.Status.STARTED, T0),
                entry("exec-1", CHANGE_E, AuditEntry.Status.APPLIED, T0.plusMinutes(1)),
                // change-F: system change
                systemEntry("exec-1", CHANGE_F, AuditEntry.Status.STARTED, T0),
                systemEntry("exec-1", CHANGE_F, AuditEntry.Status.APPLIED, T0.plusMinutes(1))));

        Map<String, AuditEntry.Status> winners = new LinkedHashMap<>();
        winners.put(CHANGE_A, AuditEntry.Status.ROLLED_BACK);
        winners.put(CHANGE_B, AuditEntry.Status.APPLIED);
        winners.put(CHANGE_C, AuditEntry.Status.APPLIED);
        winners.put(CHANGE_D, AuditEntry.Status.ROLLED_BACK);
        winners.put(CHANGE_E, AuditEntry.Status.APPLIED);
        winners.put(CHANGE_F, AuditEntry.Status.APPLIED);
        return winners;
    }

    /**
     * Seeds the given records and fails if the store did not keep all of them.
     * <p>
     * The count check is what stops a seeding mistake from being reported as a compaction bug: DynamoDB and
     * Couchbase key records by {@code (executionId, changeId, state)} and overwrite a repeat without
     * complaint, so a careless scenario would silently store fewer records than it listed.
     */
    private void seed(List<AuditEntry> entries) {
        entries.forEach(fixture::seedLedgerEntry);
        int stored = fixture.readStoredRecords().size();
        if (stored != entries.size()) {
            throw new AssertionError("Seeding stored " + stored + " records but " + entries.size()
                    + " were supplied. Two seeded records most likely shared"
                    + " (executionId, changeId, state), which key-shaped stores silently collapse.");
        }
    }

    private static AuditEntry entry(String executionId,
                                    String changeId,
                                    AuditEntry.Status status,
                                    LocalDateTime createdAt) {
        return AuditEntryTestFactory.createDeterministicAuditEntry(
                executionId, changeId, status, createdAt, false);
    }

    private static AuditEntry systemEntry(String executionId,
                                          String changeId,
                                          AuditEntry.Status status,
                                          LocalDateTime createdAt) {
        return AuditEntryTestFactory.createDeterministicAuditEntry(
                executionId, changeId, status, createdAt, true);
    }

    // ---------------------------------------------------------------------------------------------
    // Assertions
    // ---------------------------------------------------------------------------------------------

    /**
     * Compares the fields a surviving record must carry over unchanged.
     * <p>
     * Written here rather than delegating to {@code AuditEntryAssertions}, whose expectation model skips any
     * field left null and verifies timestamps against a window. Both are right for its own callers and
     * wrong here: a field that compaction dropped to null is exactly the defect this must catch, and the
     * surviving {@code createdAt} has to match exactly, not fall inside a range.
     * <p>
     * {@code metadata} is deliberately excluded. It is typed {@code Object} and comes back as a MongoDB
     * document, a string, or a map depending on the store, so comparing it would fail for reasons that have
     * nothing to do with compaction.
     */
    private static void assertFieldsPreserved(AuditEntry expected, AuditEntry actual, String what) {
        if (actual == null) {
            throw new AssertionError("Missing " + what + "; expected " + describe(expected));
        }
        assertField(what, "executionId", expected.getExecutionId(), actual.getExecutionId());
        assertField(what, "stageId", expected.getStageId(), actual.getStageId());
        assertField(what, "changeId", expected.getChangeId(), actual.getChangeId());
        assertField(what, "author", expected.getAuthor(), actual.getAuthor());
        assertField(what, "createdAt", expected.getCreatedAt(), actual.getCreatedAt());
        assertField(what, "state", expected.getState(), actual.getState());
        assertField(what, "type", expected.getType(), actual.getType());
        assertField(what, "className", expected.getClassName(), actual.getClassName());
        assertField(what, "methodName", expected.getMethodName(), actual.getMethodName());
        assertField(what, "sourceFile", expected.getSourceFile(), actual.getSourceFile());
        assertField(what, "executionMillis", expected.getExecutionMillis(), actual.getExecutionMillis());
        assertField(what, "executionHostname", expected.getExecutionHostname(), actual.getExecutionHostname());
        assertField(what, "errorTrace", expected.getErrorTrace(), actual.getErrorTrace());
        assertField(what, "systemChange", expected.getSystemChange(), actual.getSystemChange());
        assertField(what, "txType", expected.getTxType(), actual.getTxType());
        assertField(what, "targetSystemId", expected.getTargetSystemId(), actual.getTargetSystemId());
        assertField(what, "order", expected.getOrder(), actual.getOrder());
        assertField(what, "recoveryStrategy", expected.getRecoveryStrategy(), actual.getRecoveryStrategy());
        assertField(what, "transactionFlag", expected.getTransactionFlag(), actual.getTransactionFlag());
    }

    /**
     * Compares only what later properties rely on: the identity triple, the ordering field, and the flag
     * whose boxing is easy to lose.
     * <p>
     * Narrower than {@link #assertFieldsPreserved} by design. This is the one assertion that compares an
     * in-memory entry against one read back through a store, so it is the one place a store's mapper
     * fidelity leaks in. Comparing every field here would fail a store over a quirk unrelated to
     * compaction \u2014 for example a mapper that renders an absent {@code errorTrace} as an empty string.
     */
    private static void assertSeedIdentityPreserved(AuditEntry seeded, AuditEntry stored) {
        if (stored == null) {
            throw new AssertionError("Seeding stored nothing; expected " + describe(seeded));
        }
        assertField("seeded record", "executionId", seeded.getExecutionId(), stored.getExecutionId());
        assertField("seeded record", "changeId", seeded.getChangeId(), stored.getChangeId());
        assertField("seeded record", "state", seeded.getState(), stored.getState());
        assertField("seeded record", "createdAt", seeded.getCreatedAt(), stored.getCreatedAt());
        assertField("seeded record", "systemChange", seeded.getSystemChange(), stored.getSystemChange());
    }

    private static void assertField(String what, String field, Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError("Field '" + field + "' changed on " + what
                    + ": expected [" + expected + "] but was [" + actual + "]");
        }
    }

    private static void assertSameRecords(List<AuditEntry> expected, List<AuditEntry> actual, String what) {
        if (expected.size() != actual.size()) {
            throw new AssertionError("Record count changed by " + what + ": expected " + expected.size()
                    + " but was " + actual.size() + ". Compaction is not idempotent.");
        }
        for (int index = 0; index < expected.size(); index++) {
            assertFieldsPreserved(expected.get(index), actual.get(index), "record " + index + " after " + what);
        }
    }

    private static void requireOk(Result result, String what) {
        if (result == null) {
            throw new AssertionError(what + " returned null; it must return a Result");
        }
        if (result.isError()) {
            throw new AssertionError(what + " failed: " + ((Result.Error) result).getError());
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------

    private static Map<String, List<AuditEntry>> groupByChangeId(List<AuditEntry> entries) {
        Map<String, List<AuditEntry>> byChange = new LinkedHashMap<>();
        for (AuditEntry entry : entries) {
            List<AuditEntry> group = byChange.get(entry.getChangeId());
            if (group == null) {
                group = new ArrayList<>();
                byChange.put(entry.getChangeId(), group);
            }
            group.add(entry);
        }
        return byChange;
    }

    /**
     * Orders records so two reads of the same store can be compared positionally. Stores give no ordering
     * guarantee, and after compaction change id alone is unique, so it is a total order here.
     */
    private static List<AuditEntry> sortedByChangeId(List<AuditEntry> entries) {
        List<AuditEntry> sorted = new ArrayList<>(entries);
        sorted.sort((left, right) -> {
            int byChangeId = left.getChangeId().compareTo(right.getChangeId());
            return byChangeId != 0 ? byChangeId : left.getState().name().compareTo(right.getState().name());
        });
        return sorted;
    }

    private static String describe(List<AuditEntry> entries) {
        StringBuilder description = new StringBuilder("[");
        for (int index = 0; index < entries.size(); index++) {
            if (index > 0) {
                description.append(", ");
            }
            description.append(describe(entries.get(index)));
        }
        return description.append(']').toString();
    }

    private static String describe(AuditEntry entry) {
        return entry.getExecutionId() + '/' + entry.getChangeId() + '/' + entry.getState()
                + '@' + entry.getCreatedAt();
    }
}
