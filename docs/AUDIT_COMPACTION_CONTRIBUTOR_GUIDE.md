# Audit compaction — contributor guide

**Audience:** anyone (human or AI assistant) picking up one of the remaining audit-compaction chunks.
**Read this before writing code.** It carries all the context and the decisions already settled, so you
don't re-derive them — or worse, contradict them. Everything here was verified against the code; where a
file or line is named, it is the source of truth.

---

## 1. Why this exists

Flamingock used to keep the local audit trail as an append-oriented **ledger**: one row per state
transition of a change (`STARTED`, then `APPLIED` / `FAILED` / `ROLLED_BACK`, …). The current state of a
change was reconstructed by aggregating all its rows.

Per **ADR-0001** — `tech-docs/ADR-0001-separate-local-operational-state-from-centralized-audit.md` in the
separate `flamingock-documents` repo, alongside `tech-docs/CHANGE_STATE_JOURNAL_WRITE_DESIGN.md` — that
responsibility split in two:

- the **audit store** now holds only the **current state** of each change — CRU, never delete — which is
  all the operational path needs;
- the **full ledger** moves to a separate append-only **journal** of events, gated by
  `Features.JOURNAL_EVENTS`.

The journal write path already exists in all five stores. What does not exist is migrating the audit table
itself from ledger shape to current-state shape. **Three system changes** do that, in order:

| # | System change | Runs for | Does |
|---|---|---|---|
| 1 | Mongock import (**exists**) | Mongock origins only | writes the full legacy history to audit via the **old append path**, emitting no journal events |
| 2 | Journal backfill (**owned separately**) | always | reads the whole audit ledger, inserts the corresponding `JournalEvent`s |
| 3 | **Audit cleanup** (this effort) | always | collapses the audit table to one row per change: the current state |

(1) is conditional, but the pre-state of (2) is invariant — audit with full history — so every path
converges from (2) onward.

> **Important framing.** ADR-0001 says explicitly: *"Legacy records may still contain multiple entries
> created by earlier versions. Existing state-reconstruction logic can continue supporting those records.
> There is no need for a disruptive migration solely to rewrite or remove the historical data."*
>
> So compaction is an **optimisation, not a correctness fix**. What it buys: a bounded audit table, the
> ability to later enforce one record per change at the database level, and a path to a reader that no
> longer has to aggregate. Don't let anyone conclude the ADR was contradicted.

---

## 2. Where we are, and what's available to pick up

| Chunk | State |
|---|---|
| The `AuditCompactor` capability, its contract, and a shared conformance suite | **merged** — PR #964, commit `f821f204` |
| **DynamoDB** implementation | **PR open** — branch `feat/audit-compaction-dynamodb`, commit `a4fed4bc`. Use it as the reference implementation. |
| **SQL** implementation | available |
| **Couchbase** implementation | available |
| **MongoDB sync** implementation | available |
| **MongoDB reactive** implementation | available |
| The audit-cleanup **system change** | available, but see §9 — it should land last |

The four store implementations are **genuinely independent** and can go in parallel. Each is one PR that
touches only its own module (plus, in principle, nothing shared — if you find yourself editing shared code,
stop and check §8).

All five stores currently carry a **throwing stub**:

```java
@Override
public AuditCompactor getAuditCompactor() {
    return () -> {
        throw new UnsupportedOperationException(
                "Audit compaction is not yet implemented for " + getId());
    };
}
```

Your job is to replace your store's stub with a real implementation and wire the conformance suite in.

**Suggested order if you have a choice:** MongoDB sync first (simplest, and it establishes the Mongo
pattern), then MongoDB reactive *second rather than last* — it must bridge a `Publisher`-based driver to a
synchronous `Result compact()`, and it is the store most likely to need a tweak to the shared fixture's
synchronous hooks. Finding that early benefits whoever is still working.

---

## 3. The capability

`core/flamingock-core/src/main/java/io/flamingock/internal/core/external/store/AuditCompactor.java`

```java
public interface AuditCompactor {
    Result compact();
}
```

Exposed by the store, **not** by the persistence — compaction is table-global, whereas
`AuditPersistenceFactory.get(stageId)` is per-stage:

```java
public interface CommunityAuditStore extends AuditStore<CommunityAuditPersistence> {
    CommunityLockService getLockService();
    AuditCompactor getAuditCompactor();     // abstract: a store that can't compact is a compile error
}
```

`Result` is `io.flamingock.internal.util.Result` from the **external** artifact
`io.flamingock:flamingock-general-util` (pinned `1.6.0` in the root `build.gradle.kts`). It is payload-free
— only `Result.OK()` and `Result.Error(Throwable)` — and **cannot be changed from this repo**. That is why
`compact()` returns no counts; the shape can be widened later without breaking callers that only check
`isError()`.

---

## 4. The contract — all 13 clauses

The authoritative version is the javadoc on `AuditCompactor.compact()`. **Read it.** Condensed here so you
can hold it in your head:

1. **Semantically a no-op for the operational path.** After `compact()`,
   `AuditReader.getAuditSnapshotByChangeId()` must map exactly the same change ids to exactly the same
   effective entries as before. `CommunityExecutionPlanner` is the only operational consumer and reads that
   map. Compaction changes *how* state is stored, never *what* the state is.
2. **The survivor is not yours to choose.** It must be what `AuditSnapshotBuilder` →
   `AuditEntry.getMostRelevant` → `shouldBeReplacedBy` selects: later `createdAt` wins; on *exactly equal*
   `createdAt`, higher `AuditEntry.Status` priority wins (`STARTED` 1 < `APPLIED` 2 < `FAILED` 3 <
   `ROLLED_BACK` 4 < `ROLLBACK_FAILED` 5 < `MANUAL_MARKED_AS_APPLIED` 6 < `MANUAL_MARKED_AS_ROLLED_BACK` 7).
   **`ORDER BY created_at DESC LIMIT 1` is NOT conformant** — it resolves the tie arbitrarily. If you select
   server-side, you must transcribe the rule faithfully.
3. **At most one stored record per `changeId`** afterwards. Keying on change id alone is safe because
   `LoadedPipeline.validate()` rejects duplicate change ids across all stages.
4. **Total, never pipeline-scoped.** Every change id present is compacted, including changes no longer
   declared in any stage and `systemChange == true` entries. **Do not consult the pipeline** — dropping an
   undeclared change would permanently erase the record that it was applied.
5. **Nothing lost beyond the superseded records.** The survivor's fields are preserved verbatim — no
   re-derivation, no re-stamping of `createdAt`, no normalising of `executionId`.
6. **Idempotent.** Twice = once. Safe on an already-compacted store, on an empty store, and as a retry
   after a partial previous run.
7. **NOT required to be atomic.** No store can be, so don't contort yourself trying.
8. **Survivor first, deletes second.** *This is the most important clause.* Where the physical record key
   changes under compaction, write the survivor under its new key **before** deleting any old-keyed record.
   Invariant: *at every instant, every change id that had a record still has one.* A crash may leave
   duplicates — recoverable, because the selection rule still yields the same winner and the next run
   converges — but must **never** leave a change with zero records, which is unrecoverable: the planner
   would treat the change as never applied and **re-execute it against the target system**.
9. **Indexes and schema are out of scope.** Do not create, drop or alter any index, constraint, table or
   collection. Enforcing one-record-per-change at the database level is a separate, later concern.
10. **No journal events.** Compaction changes storage layout, not change state. Bypass the
    `AuditWriter.writeEntry` path that the persistence takes while `JOURNAL_EVENTS` is on — emitting events
    here would corrupt the very ledger the backfill just produced.
11. **The normal write path must still work afterwards.** An ordinary current-state write for a
    pre-existing change id must succeed once compaction has run. Not a tautology — see the SQL briefing.
12. **The caller guarantees exclusivity.** This runs as a system change under the Flamingock lock, so a
    single writer may be assumed. You need not be correct against a concurrent ordinary execution.
13. **Lifecycle.** `getAuditCompactor()` may only be called after `initialize(...)`, and must throw
    `IllegalStateException` otherwise.

**Failure signalling: report, don't throw.** Infrastructure errors come back as `Result.Error(cause)`,
matching `AuditWriter.writeEntry`. Clause 8 must still hold on the error path. **Fail-fast** is the agreed
policy: abort on the first failing change and return `Result.Error`. Changes are independent, so progress
is preserved and the next run converges.

---

## 5. The recipe

Follow the DynamoDB shape (`community/flamingock-dynamodb-auditstore/src/main/java/io/flamingock/store/dynamodb/internal/DynamoDBAuditCompactor.java`):

1. **A separate package-private class** `<Store>AuditCompactor implements AuditCompactor` in your store's
   `internal/` package, constructed with whatever table/collection handle it needs. *Not* a `compact()`
   method on the repository — a separate class can be unit-tested with a mocked handle and **no
   reflection**, which matters a lot here (see §8).
2. **The repository exposes it**: a `public AuditCompactor getAuditCompactor()` that throws
   `IllegalStateException` if its handle is null. (Public because the store class lives in the parent
   package — same as `getAuditHistory()`.)
3. **The store delegates**, mirroring how `getAuditReader()` already resolves the lazy handle:
   ```java
   @Override
   public AuditCompactor getAuditCompactor() {
       if (auditRepository == null) {
           throw new IllegalStateException("AuditStore not initialized - call initialize first");
       }
       auditRepository.initialize(autoCreate);   // same lazy idiom as getAuditReader()
       return auditRepository.getAuditCompactor();
   }
   ```
4. **Algorithm**: read all records → group by `changeId` → per change, pick the survivor via
   `AuditSnapshotBuilder`, write it, then remove the superseded ones.

**Reuse `AuditSnapshotBuilder` — do not reimplement selection.** That is what guarantees clause 1.

### First decide whether you need record identity at all — most stores don't

**Read this before copying the DynamoDB shape.** "Scan everything, then pick the winner per change" is
*already implemented*: `AuditReader.getAuditSnapshotByChangeId()` is a default method that runs
`getAuditHistory()` through `AuditSnapshotBuilder` and hands back `Map<String, AuditEntry>` — exactly the
survivors, keyed by change. If you can work from that, **your compactor is a short loop and you can skip
everything below.**

You can work from it when **both** are true:

- **You don't rekey** — so you never have to write the stored record back, only delete others.
- **You can address records from an `AuditEntry` alone** — so you don't need a physical row/doc identity
  that `AuditEntry` doesn't carry.

| Store | Can work from `getAuditSnapshotByChangeId()`? | Why |
|---|---|---|
| MongoDB sync / reactive | **yes** | no rekey, and the unique index on `(executionId, changeId, state)` makes the survivor addressable by filter |
| SQL | **yes** | no rekey, and it deletes by `change_id` and re-inserts from an `AuditEntry` |
| DynamoDB / Couchbase | **no** | they rekey, and the physical key is not derivable — see below |

So for MongoDB the whole implementation is roughly:

```java
for (Map.Entry<String, AuditEntry> survivor : auditReader.getAuditSnapshotByChangeId().entrySet()) {
    AuditEntry winner = survivor.getValue();
    collection.deleteMany(Filters.and(
            Filters.eq(KEY_CHANGE_ID, survivor.getKey()),
            Filters.nor(Filters.and(
                    Filters.eq(KEY_EXECUTION_ID, winner.getExecutionId()),
                    Filters.eq(KEY_STATE, winner.getState().name())))));
}
```

Nothing is rewritten at all, so clause 5 (preserve verbatim) is satisfied trivially and the survivor keeps
its original `_id`. One atomic operation per change. And for SQL, per change inside one JDBC transaction:
`DELETE … WHERE change_id = ?` then insert the survivor — note the re-inserted row gets a fresh surrogate
`id`, which is harmless since nothing reads it and it is not in `AuditEntry`.

### Why DynamoDB and Couchbase can't do that

Two reasons, and the second is the one that actually blocks it:

1. They must write the stored record back under a new key, and rebuilding it from an `AuditEntry` **launders
   it** — `txStrategy` becomes `NON_TX` and `recoveryStrategy` becomes `MANUAL_INTERVENTION` for records that
   never had those attributes.
2. **The physical key is not derivable from an `AuditEntry`.** A record stored at
   `executionId#changeId#state` and one stored at the bare `changeId` produce an *identical* `AuditEntry` —
   same fields, no distinguishing mark. So from the snapshot you cannot tell which records live at which
   keys, which means you cannot know what to delete, nor whether a change is already compacted. You need the
   store's own record representation (DynamoDB's `AuditEntryEntity.getPartitionKey()`, Couchbase's
   `META().id`).

If that's you, map converted entries back to their originals by identity:

```java
Map<AuditEntry, StoredRecord> originalOf = new IdentityHashMap<>();
AuditSnapshotBuilder builder = new AuditSnapshotBuilder();
for (StoredRecord record : records) {
    AuditEntry converted = record.toAuditEntry();
    originalOf.put(converted, record);
    builder.addEntry(converted);
}
StoredRecord survivor = originalOf.get(builder.buildList().get(0));
```

This is sound because `AuditSnapshotBuilder` returns the very instances it was given, and **`AuditEntry`
declares no `equals()`/`hashCode()`**, so the builder's internal `winner.equals(newEntry)` is identity
comparison. (Verified; don't "improve" it by adding `equals` to `AuditEntry`.)

Short-circuit single-record groups — there is nothing to decide, and conversion is the one step that can
fail on a malformed legacy record.

---

## 6. Per-store briefing

### The one question that shapes everything: **does your store rekey?**

| Store | Ledger record key | Current-state key | Rekey? | Compaction is… |
|---|---|---|---|---|
| MongoDB sync | auto `_id` ObjectId | same `_id`, filter on `changeId` | **no** | delete the superseded docs |
| MongoDB reactive | auto `_id` ObjectId | same `_id`, filter on `changeId` | **no** | delete the superseded docs |
| SQL | surrogate auto-increment `id` | same `id`, `UPDATE … WHERE change_id` | **no** | delete the superseded rows |
| DynamoDB | `executionId#changeId#state` | bare `changeId` | **yes** | a rekey |
| Couchbase | `executionId#changeId#state` | bare `changeId` | **yes** | a rekey |

Clause 8 (survivor-before-delete) is **critical** for the two that rekey and trivially satisfied by the
three that don't. But all five must still be idempotent.

The three that don't rekey can almost certainly skip the entity-level scan entirely and build on
`getAuditSnapshotByChangeId()` — see "First decide whether you need record identity at all" in §5 before
you write anything.

---

### MongoDB sync — `community/flamingock-mongodb-sync-auditstore`

- `MongoDBSyncAuditRepository` has three write methods: `save(ClientSession, entry)` and `save(entry)` —
  both `replaceOne(filter: changeId, upsert)` — and `append(entry)` —
  `replaceOne(filter: executionId+changeId+state, upsert)`. The collection **has a UNIQUE index on
  `(executionId, changeId, state)`**, created by `CollectionInitializator`.
- `_id` is an auto ObjectId and `getAuditHistory()` maps it away — but **you don't need it**. Because the
  triple is unique, you can identify the survivor by its triple and delete the rest with one filter.
- `CollectionHelper` already exposes `deleteMany(DOCUMENT)` and `dropIndex(name)`.
- **Easiest conformant shape:** per change, `replaceOne(changeId, winnerDoc, upsert)` is unnecessary (no
  rekey) — just `deleteMany(changeId == X AND NOT(executionId == w.exec AND state == w.state))`. One round
  trip per change, and it is atomic per operation.
- A `ClientSession` transaction is available if you want the whole thing atomic. Permitted, not required.
- **Trap (relevant to a FUTURE step, not yours):** `CollectionInitializator`'s "rule (a)" drops any unique
  non-`_id` index that doesn't match a declared spec. So if anyone ever makes compaction create a unique
  index on `changeId`, the next startup silently drops it and recreates the triple index. Clause 9 says
  don't touch indexes — this is why.

### MongoDB reactive — `community/flamingock-mongodb-reactive-auditstore`

Everything above applies. The only difference is plumbing: the module already bridges reactive to
synchronous with `PublisherSync.first(...)` / `PublisherSync.collect(...)` / `PublisherSync.complete(...)`
(see `MongoDBReactiveAuditRepository`). Use the same idiom; `compact()` is synchronous by contract.

If the shared fixture's synchronous hooks turn out awkward here, say so early — see §2.

### SQL — `community/flamingock-sql-auditstore`

The mechanism is the simplest of the five; the surrounding surface is the widest.

- `SqlAuditRepository.save(Connection, entry)` is `UPDATE … WHERE change_id = ?` and **throws
  `IllegalStateException` when `updatedRows > 1`**. This makes clause 11 a *real* constraint for you: a
  compaction that leaves two rows passes every snapshot-level check and then **breaks the next run**. The
  conformance suite's `verifySubsequentCurrentStateWriteSucceeds` is aimed squarely at this.
- **SQL is the only store with no unique constraint at all** — just the surrogate `id` PK. So genuinely
  identical `(executionId, changeId, state)` triples *can* exist, and the contract must not assume
  otherwise. Disambiguating them needs the surrogate `id`, which is **not** mapped into `AuditEntry`.
- **Portable approach that avoids all of that:** per change, in one JDBC transaction,
  `DELETE FROM <table> WHERE change_id = ?` then `INSERT` the survivor using the existing
  `dialectHelper.getInsertSqlString(...)`. Atomic per change, needs no surrogate id, handles true
  duplicates, and requires **zero new dialect-specific SQL**. Strongly recommended over a
  `DELETE … WHERE id NOT IN (SELECT MAX(id) … GROUP BY …)`, which has to be written differently for MySQL,
  Oracle, DB2, Informix, Firebird and Sybase.
- **11 dialects**: `MYSQL, MARIADB, POSTGRESQL, SQLITE, H2, SQLSERVER, SYBASE, FIREBIRD, INFORMIX, ORACLE,
  DB2`. The test task is driven by the `sql.test.dialects` system property; the Gradle default is
  `mysql,oracle` on CI and `mysql,oracle,sqlserver` locally. **Nine dialects get no CI coverage** — run
  more locally before you ship.
- Known per-dialect quirks already in the code: Informix needs an explicit `setAutoCommit(true)` for audit
  writes; nullable booleans map to different JDBC types per dialect; `created_at` column types truncate
  sub-second precision differently (which is why the conformance suite seeds whole-second timestamps).
- **Cross-repo warning.** `SqlAuditorDialectHelper` is **not in this repo** — it lives in
  `library/flamingock-java-sql`, module `flamingock-sql-util`, consumed here as the published artifact
  `io.flamingock:flamingock-sql-util:1.3.2`. If you need new dialect SQL, that means a change + release in
  the other repo and a `sqlVersion` bump here. **Check first whether you can avoid it** — the
  delete-then-insert approach above is specifically designed so you can.

### Couchbase — `community/flamingock-couchbase-auditstore`

Rekeys, like DynamoDB, but with better tools.

- `CouchbaseAuditor.append(entry)` upserts at `toKey(entry)` = `executionId#changeId#state`;
  `contributeToTransaction(ctx, entry)` writes at `entry.getChangeId()`. That asymmetry is the rekey.
- **Couchbase transactions have no `upsert`.** The existing code works around it with
  `ctx.get(...)` then `ctx.insert(...)` on `DocumentNotFoundException` — see
  `CouchbaseAuditor.contributeToTransaction`. You will likely need the same idiom.
- N1QL is available via `cluster.query(...)`. `CouchbaseCollectionHelper` has `selectAllDocuments` and
  `deleteAllDocuments` and the query templates they use. The collection is guaranteed a **primary index**
  only — no secondary indexes.
- **The trap that will cost you an afternoon if nobody tells you:** `selectAllDocuments` runs
  `SELECT <collection>.* FROM …`, which projects **document fields only — not `META().id`**. So it cannot
  tell you a document's key. And you *cannot* reconstruct the key from the fields, because a document
  already at the `changeId` key has exactly the same fields as one at the ledger key. **You must query
  `META().id` explicitly** (e.g. `SELECT META().id AS id, <collection>.* FROM …`) or you have no way to
  know which documents to delete. This is Couchbase's equivalent of DynamoDB's "the test-kit storage drops
  the partition key".
- Force `QueryScanConsistency.REQUEST_PLUS` on your reads, as `selectAllDocuments` already does — a stale
  read could pick the wrong survivor.
- No index work needed: rekeying to `changeId` makes the document key itself enforce one-per-change.

### DynamoDB — already done, read it

`DynamoDBAuditCompactor` is the reference. Two subtleties in it that generalise:

- **The survivor written back is the stored record with only its key changed**, never one rebuilt from an
  `AuditEntry`. Rebuilding launders the record: it resets the key and materialises defaults for attributes
  the record never had (`txStrategy` → `NON_TX`, `recoveryStrategy` → `MANUAL_INTERVENTION`).
- **The keys to delete are captured *before* the survivor is rekeyed.** The survivor is one of the scanned
  records and is mutated in place, so reading its key afterwards reports the *new* key and spares its own
  old row — which then never gets deleted, and a later run can find two records whose keys both already
  equal the change id and delete neither, leaving the change **permanently duplicated instead of
  converging**. This was a real bug caught by its own test. If your store rekeys, you have this bug waiting
  for you.

---

## 7. The shared conformance suite — wire it in, don't reinvent it

`utils/test-util/src/main/java/io/flamingock/core/kit/audit/compaction/`

| Class | Purpose |
|---|---|
| `AuditCompactionFixture` | six hooks the suite needs from your store |
| `AuditStorageCompactionFixture` | **the default wiring — most stores need only this** |
| `AuditCompactionConformance` | the properties themselves |

All five stores already declare `testImplementation(project(":utils:test-util"))`, so **no build changes
are needed**. Wiring is one line:

```java
new AuditCompactionConformance(
        new AuditStorageCompactionFixture(auditStore, <YourStore>AuditStorage, stageId));
```

`AuditStorage.addAuditEntry` writes **raw**, bypassing your store's write path, which is exactly what
seeding a ledger requires. Verified for all four remaining stores: Couchbase seeds at the ledger doc key,
SQL does a plain `INSERT` (so duplicates are possible), both Mongo kits do a bare `insertOne`.

### The eleven properties

`verifySeedingRoundTripsFaithfully`, `verifySnapshotPreserved`, `verifyOneRecordPerChange`,
`verifyIsFixpoint`, `verifySubsequentCurrentStateWriteSucceeds`, `verifyUnknownChangesPreserved`,
`verifyAlreadySingleRecordUntouched`, `verifyEmptyStoreIsNoOp`,
`verifyTimestampTieBreaksOnStatusPriority`, `verifySystemChangeEntriesPreserved`, and
`verifyLargeLedgerIsCompacted(int, int)` (opt-in, not in `verifyAll()`).

Give each its own `@Test` so a failure names the clause it broke. The SQL suite may prefer one
`verifyAll()` call per dialect from its existing `@ParameterizedTest`, to avoid spinning an Oracle
container per property — the properties are plain methods precisely so that choice is yours.

### Two things to know before you debug a confusing failure

- **`verifySubsequentCurrentStateWriteSucceeds` needs `JOURNAL_EVENTS` enabled** (the suite does it itself
  and restores it in a `finally`) because the current-state write path is flag-gated. It therefore also
  needs your journal table/collection to exist and a per-stage sequencer — i.e. the full
  `getPersistenceFactory().get(stageId)` wiring, including an **initialized target system**. Also add
  `FeatureFlag.remove(Features.JOURNAL_EVENTS)` to your `@AfterEach`: the flag is process-global and every
  test in the module shares the JVM.
- **The seeding rule:** never seed two records with identical `(executionId, changeId, state)`. Mongo's
  unique index rejects it; DynamoDB and Couchbase silently overwrite; SQL keeps both and diverges from the
  others. "Duplicates" always means *same `changeId`, different `state` and/or `executionId`*. Use
  `AuditEntryTestFactory.createDeterministicAuditEntry(executionId, changeId, status, createdAt, systemChange)`
  — the older factory methods stamp a random `executionId` and `LocalDateTime.now()` and cannot express a
  realistic ledger.

### The suite cannot see your physical keys

`AuditStorage` maps records to `AuditEntry`, which drops the physical key. So **if your store rekeys, the
shared suite alone cannot prove the rekey happened** — you must add a store-specific raw read. DynamoDB does
this with a 6-line `storedAuditRecords()` returning raw entities; Couchbase will need the `META().id` query
from §6.

---

## 8. The test bar

Compaction is destructive and partially non-atomic. The bar set by the DynamoDB PR is **35 tests**, and the
split matters:

**Unit tests against a mocked handle, no container (20 in DynamoDB's case).** The properties that matter
most — survivor written before anything is deleted, reads unbounded and consistent, failure deletes
nothing, fail-fast — are about the **sequence of calls**. A mock observes that sequence directly; a
container only lets it be inferred from the end state. This is also the only part contributors without
Docker can run. DynamoDB's set covers: put-before-delete via `InOrder`; the scan's consistency flag and
absence of `limit`/`filter`; multi-page reads; survivor identity; the tie-break; already-compacted
skipping; failure on write deleting nothing; failure on delete after the survivor is safe; fail-fast; a
malformed record failing with an actionable message; order-independence; and
`verifyNoMoreInteractions` — which proves clauses 9 and 10 **by construction**.

One of those twenty guards the *repository*, not the compactor: `DynamoDBAuditRepositoryTest` asserts that
`getAuditHistory()` reads strongly consistently, unbounded and unfiltered. It exists because none of those
three properties was enforced anywhere, and all three fail silently — a truncated or stale history makes the
planner mis-decide with no error. If your store's audit read has equivalent requirements, pin them the same
way. Note it deliberately duplicates the compactor's equivalent assertions rather than sharing a helper: the
two reads share a flag value, not a reason (there, a stale read makes compaction delete the real survivor;
here it misleads the planner), and collapsing them into one symbol would hide why either matters. Resist the
urge to DRY them.

**Integration tests against a real container (15 in DynamoDB's case).** The shared suite, plus your
store-specific physical-key assertions, plus one convergence test from a hand-made half-finished state.

**Mutation-test your critical assertions.** Break the implementation deliberately and confirm a test fails.
Two real bugs in the DynamoDB work were found this way. A green suite that cannot fail is worthless.

### Known gaps in the DynamoDB work — the bar, honestly

Don't treat the DynamoDB PR as the ceiling; these were identified and consciously deferred, and you may be
able to do better:

1. **The mocked table is stateless**, so those tests assert calls, not resulting state. A *stateful* fake
   (~30 lines: a mock with `thenAnswer` closures over a `LinkedHashMap`) would allow injecting a failure at
   every step in turn, re-running to convergence, and asserting the clause-8 invariant — *every changeId
   always has at least one record* — after **every** operation. That would be the strongest safety argument
   available, and it needs no Docker. Consider building it; it is reusable across stores.
2. **Only a blank `changeId` is tested for malformed records.** A legacy record missing `state`, `type`,
   `executionMillis` or `systemChange` produces a bare `NullPointerException`. It is fail-closed (it happens
   before any write, so nothing is lost) so this is a diagnosability gap, not a safety one — but it's worth
   closing.
3. **Clause 13 is untested** — `getAuditCompactor()` before `initialize()`. Five lines, no Docker.

Already closed, so don't redo it: the repository's audit read is now covered by `DynamoDBAuditRepositoryTest`
(consistency, no limit, no filter), each assertion mutation-verified.

---

## 9. The audit-cleanup system change (step 7)

Should land **after** the stores, because it calls `getAuditCompactor().compact()` and every store would
otherwise hit the throwing stub.

What it needs:

- **Registration.** System changes are contributed by the annotation processor and ordered by an `order`
  string. Today the *only* system change is the Mongock importer
  (`legacy/mongock-support/.../MongockAnnotationProcessorPlugin.getImporterChange`, `order = "00100"`), so
  the system stage only exists when `mongock-support` is present
  (`PipelineStructureBuilder.buildSystemStageIfNeeded` returns empty for an empty list). The cleanup change
  must be contributed **unconditionally** by core's processor, with an order **after** the journal backfill.
- **Injection.** One line next to the existing `AuditPersistenceFactory` registration in
  `AbstractChangeRunnerBuilder` (~line 228):
  `new Dependency(AuditCompactor.class, auditStore.getAuditCompactor())`, then received as a
  `@NonLockGuarded` method parameter, exactly as `MongockImportChange.importHistory` receives its
  dependencies.
- **It must treat `Result.Error` as a change failure.** The capability reports; the system change decides.
  Swallowing the error would record the change as applied over a store that was never compacted — and in
  SQL that surfaces much later as `"Current audit state update matched 2 rows"`.
- **Gating.** Open question worth settling deliberately: with `JOURNAL_EVENTS` off, does it run at all? It
  must not be silently skipped-but-recorded, or it will never re-run when it actually matters.

Related and **separate**: the Mongock import change (step 1) must keep writing the **full history via the
old append path with no journal events**. It currently calls `writeEntry`, which is flag-routed — so it
needs an explicit legacy-append route. `append` already exists in every repository; it just isn't reachable
from the importer.

---

## 10. Pre-existing bugs — don't be surprised, don't fix in your PR

- **DynamoDB silently never persists `errorTrace` or `metadata`.** Their getters return `String` while
  their setters take `Object`, so `java.beans.Introspector` doesn't pair them and `BeanTableSchema` drops
  both as read-only. Verified: the mapped schema has 19 attributes and neither is among them. Nothing
  noticed because every test and factory leaves them null. Locked by
  `AuditEntryEntitySchemaTest` so a fix is deliberate. **Needs its own issue.**
- **`AuditEntryEntity.toAuditEntry()` turns an absent `errorTrace` into `""`** (`Objects.toString(x, "")`).
  This is why two conformance properties compare store-to-store rather than in-memory-to-store. If you see
  a mapper asymmetry in your store, prefer fixing the comparison's *scope* over normalising values — the
  suite has no business encoding an opinion on whether a mapper quirk is a bug.
- **`executionMillis` is `Long` in `AuditEntryEntity` but primitive `long` in `AuditEntry`'s constructor**,
  so a record missing it NPEs on conversion. Pre-existing on every read, including `getAuditHistory()`.
- **`AuditEntry` has no `equals()`.** Field-by-field comparison is required in tests. `compareTo` exists but
  is ordering, not identity.

---

## 11. Definition of done

```bash
cd library/flamingock-java

./gradlew spotlessApply && ./gradlew spotlessCheck     # license headers: Copyright 2026 on new files

# your store, unit + integration
./gradlew :community:flamingock-<store>-auditstore:test

# the in-memory conformance hosts must stay green if you touch anything shared
./gradlew :core:flamingock-test-support:test --tests "*CompactionConformanceTest"
./gradlew :e2e:core-e2e:test --tests "*CompactionConformanceTest"

./gradlew clean build                                   # authoritative; see CLAUDE.md validation policy
```

- **`clean build` from the root is the only authoritative check** (CLAUDE.md Tier 2). A passing per-module
  run is not sufficient. With Docker available it takes a long time — budget for it. Without Docker, every
  container-backed test fails with `DockerClientProviderStrategy`; say so explicitly rather than implying
  the change is verified.
- `spotless` is **deliberately detached from the build lifecycle** (`flamingock.license.gradle.kts` removes
  it from `check`), so `build` does **not** catch header drift. Run it explicitly. CLAUDE.md is wrong on
  this point.
- **Java 8 toolchain** in these modules: no `var`, no `List.of`, no `Map.of`.
- **Conventional Commits**, body required for `feat`, and **no Claude co-author trailer** (this repo's
  CLAUDE.md says so explicitly).
- No `build.gradle.kts` changes should be necessary. If you think you need one, re-read §7.

---

## 12. Quick reference — the files you'll touch

```
core/flamingock-core/.../external/store/AuditCompactor.java          the contract (read, don't change)
core/flamingock-core/.../external/store/CommunityAuditStore.java      declares getAuditCompactor()
core/flamingock-core-commons/.../audit/AuditSnapshotBuilder.java      the selection rule (reuse)
core/flamingock-core-commons/.../audit/AuditEntry.java                Status priorities, shouldBeReplacedBy

utils/test-util/.../kit/audit/compaction/                             the shared conformance suite
utils/test-util/.../kit/audit/AuditEntryTestFactory.java              createDeterministicAuditEntry(...)
utils/test-util/.../kit/audit/AuditStorage.java                       raw seed/read, per store

community/flamingock-dynamodb-auditstore/.../internal/DynamoDBAuditCompactor.java       reference impl
community/flamingock-dynamodb-auditstore/src/test/.../DynamoDBAuditCompactorTest.java   reference unit tests
community/flamingock-dynamodb-auditstore/src/test/.../DynamoDBAuditCompactionConformanceTest.java
community/flamingock-dynamodb-auditstore/src/test/.../DynamoDBAuditRepositoryTest.java  pins the audit read
```

Your PR should touch **only** your store's module. If you need to change the shared suite or the contract,
raise it rather than doing it quietly — four people are working against these in parallel.
