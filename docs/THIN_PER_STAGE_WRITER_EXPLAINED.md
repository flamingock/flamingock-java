# The thin per-stage writer, explained

This document walks through the "thin per-stage writer" (option B): what it is, where it's built, where it's used, and why it's the safe shape. All code here is **illustrative** — it sketches the design, it is not wired into the codebase.

---

## 1. The idea in one line

A **singleton** owns the heavy database resources. A **thin, per-stage** object wraps those singletons together with a fresh stream cursor, and exposes a clean `write(changeState)`. The per-stage object is cheap to build (a `long` counter plus a few references), so creating one per stage costs nothing.

---

## 2. Lifecycle map

"Thin per-stage writer" only makes sense once you see what lives how long:

| Lifetime | Objects | Holds |
|----------|---------|-------|
| **Singleton** (startup) | `MongoClient`, `ChangeStateRepo`, `JournalRepo`, `TxRunner`, `JournaledStateWriterProvider`, `JournalEventReader` | the heavy / shared DB resources |
| **Per stage** (in `executeStage`) | `JournalEventFactory` (the cursor), **`JournaledStateWriter`** (the thin facade), `ChangeProcessStrategyFactory` | the seeded stream cursor — nothing heavy |
| **Per change** (in `factory.build()`) | `ChangeStateOperation` (was `AuditStoreStepOperations`), the strategy | `txType`, `targetSystemId` |

The **thin per-stage writer** is the middle row. It is created fresh per stage, but it only wraps a `long` counter plus **references** to the singleton repos. The expensive stuff (the Mongo client, the collections) is never rebuilt.

---

## 3. The pieces

### 3.1 Pure sequencer / factory (per stage, no persistence dependency)

Holds the stream cursor and stamps each event. It is a plain in-memory object — no database dependency — so it is trivially testable.

```java
public final class JournalEventFactory {
    private final String streamId;
    private long nextSequence;

    public JournalEventFactory(String streamId, long initialSequence) {
        this.streamId = streamId;
        this.nextSequence = initialSequence;   // seeded from outside
    }

    public <T> JournalEvent<T> newEvent(T payload, JournalEventType type) {
        return new JournalEvent<>(
                UUID.randomUUID().toString(),   // eventId
                type,
                streamId,
                nextSequence++,                 // in-memory, safe: one writer per stage
                Instant.now(),                  // occurredAt
                payload);
    }
}
```

### 3.2 The thin per-stage writer

This is the object in question. It references singletons (`repos`, `txRunner`) and holds one per-stage thing (`eventFactory`). Its `write(...)` takes **only** the domain object — the journal envelope is built and appended internally.

```java
public final class JournaledStateWriter {
    private final ChangeStateRepo changeStateRepo;  // singleton
    private final JournalRepo     journalRepo;      // singleton (horizontal outbox)
    private final TxRunner        txRunner;         // singleton (owns the tx per write)
    private final JournalEventFactory eventFactory; // per stage (the cursor)

    JournaledStateWriter(ChangeStateRepo changeStateRepo, JournalRepo journalRepo,
                         TxRunner txRunner, JournalEventFactory eventFactory) {
        this.changeStateRepo = changeStateRepo;
        this.journalRepo = journalRepo;
        this.txRunner = txRunner;
        this.eventFactory = eventFactory;
    }

    /** The clean call site: caller hands over only the domain object. */
    public Result write(ChangeState state) {
        JournalEvent<ChangeState> event = eventFactory.newEvent(state, JournalEventType.CHANGE_STATE);
        return txRunner.inTransaction(tx -> {
            changeStateRepo.write(tx, state);   // domain write
            journalRepo.append(tx, event);      // outbox append — atomic with the above
            return Result.OK();
        });
    }
}
```

### 3.3 The singleton provider that builds it

This is the seam where per-stage-ness becomes visible. It lives in the persistence and holds the singleton resources; `forStream(...)` cheaply wraps a fresh cursor around them.

```java
public final class JournaledStateWriterProvider {           // singleton, lives in the persistence
    private final ChangeStateRepo changeStateRepo;
    private final JournalRepo journalRepo;
    private final TxRunner txRunner;

    public JournaledStateWriterProvider(ChangeStateRepo r, JournalRepo j, TxRunner t) {
        this.changeStateRepo = r; this.journalRepo = j; this.txRunner = t;
    }

    /** Cheap: just wraps a fresh cursor around the singleton resources. */
    public JournaledStateWriter forStream(String streamId, long initialSequence) {
        JournalEventFactory factory = new JournalEventFactory(streamId, initialSequence);
        return new JournaledStateWriter(changeStateRepo, journalRepo, txRunner, factory);
    }
}
```

### 3.4 The MongoDB concrete for `TxRunner`

This is where "the persistence owns the transaction" and the atomicity live. The repos pull the `ClientSession` out of `TxSession`, so both writes land on the same session inside `withTransaction` → atomic.

```java
public final class MongoTxRunner implements TxRunner {     // singleton, built with the MongoClient
    private final MongoClient client;
    public MongoTxRunner(MongoClient client) { this.client = client; }

    @Override
    public <R> R inTransaction(Function<TxSession, R> work) {
        try (ClientSession session = client.startSession()) {
            return session.withTransaction(() -> work.apply(new MongoTxSession(session)));
        }
    }
}
```

For DynamoDB, `TxRunner` instead accumulates both puts into one `TransactWriteItems` — same interface, different guts. That is why `JournaledStateWriter` itself is DB-agnostic.

---

## 4. Where it's built and used

### 4.1 Built once per stage, in `StageExecutor.executeStage(...)`

```java
public class StageExecutor {
    private final JournaledStateWriterProvider writerProvider; // singleton, injected
    private final JournalEventReader journalEventReader;       // singleton, injected
    // ...

    public Output executeStage(ExecutableStage stage, ExecutionContext ctx, Lock lock) {
        String streamId = stage.getName();                     // stream == stage

        // ONE seed read per stage
        long initialSequence = journalEventReader.getLastEventByStream(streamId)
                .map(e -> e.getStreamSequence() + 1)
                .orElse(1L);

        // build the thin per-stage writer once
        JournaledStateWriter stateWriter = writerProvider.forStream(streamId, initialSequence);

        // hand it to the per-stage factory (already per-stage in today's code)
        ChangeProcessStrategyFactory factory = new ChangeProcessStrategyFactory(targetSystemManager)
                .setExecutionContext(ctx)
                .setStateWriter(stateWriter)                   // was setAuditWriter
                .setDependencyContext(/*...*/)
                .setLock(lock)
                .setNonGuardedTypes(nonGuardedTypes);

        getChangesStream(stage)
                .map(factory::setChange)                       // per change
                .map(ChangeProcessStrategyFactory::build)      // build() passes the SAME stateWriter down
                .map(ChangeProcessStrategy::applyChange)
                // ... unchanged
                ;
    }
}
```

### 4.2 Used by the per-change operation, which stays 100% ChangeState

```java
public class ChangeStateOperation {                 // was AuditStoreStepOperations, one per change
    private final JournaledStateWriter stateWriter; // the per-stage instance, shared across changes
    private final AuditTxType txType;               // per-change (cohesive in the ctor)
    private final String targetSystemId;            // per-change

    public ChangeStateOperation(JournaledStateWriter stateWriter, AuditTxType txType, String targetSystemId) {
        this.stateWriter = stateWriter;
        this.txType = txType;
        this.targetSystemId = targetSystemId;
    }

    public Result auditExecution(ExecutionStep step, ExecutionContext ctx, LocalDateTime appliedAt) {
        RuntimeContext runtime = RuntimeContext.builder()
                .setExecutionStep(step).setAppliedAt(appliedAt).build();
        ChangeState state = new ExecutionAuditContextBundle(
                step.getLoadedChange(), ctx, runtime, txType, targetSystemId).toChangeState();
        return stateWriter.write(state);            // no event awareness at all
    }

    // auditStartExecution / auditManualRollback / auditAutoRollback: identical shape
}
```

---

## 5. Why this shape is the safe one

- **`stateWriter` is created once per stage** (`writerProvider.forStream(...)`) and the *same instance* is shared by every change in that stage — because `ChangeProcessStrategyFactory` is already per-stage and just calls `setChange` per change. So the cursor is continuous across the whole stage, exactly one instance.
- **The cursor lives in `JournalEventFactory`, which dies with the stage.** No map of streams, no cross-stage state, no "are stages concurrent?" reasoning. One stage holds the lock → one `stateWriter` exists → the `nextSequence++` cannot race.
- **Only the facade is per-stage; the `MongoClient` / repos / `TxRunner` are singleton** behind it. Building a `JournaledStateWriter` per stage is a couple of field assignments.
- **The operation never touches the journal.** It produces a `ChangeState` and calls `write(state)`. The horizontal journaling (build event + atomic outbox append) is entirely inside the writer.

---

## 6. The fallback (if you'd rather not add the facade)

If you prefer not to introduce the per-stage facade at all, the alternative is a **singleton writer** with `write(state, event)`, where the **operation** builds the event via an injected per-stage `JournalEventFactory`:

- Same atomicity (state + event in one transaction).
- Same per-stage cursor.
- But the operation gains one line of journal awareness (it builds the event), and you skip `JournaledStateWriter` / `JournaledStateWriterProvider`.

| | **Per-stage facade (option B)** | **Singleton writer (fallback)** |
|---|---|---|
| Write API | `write(state)` | `write(state, event)` |
| Who builds the event | the writer | the operation |
| Operation purity | fully pure ChangeState | one line of journal awareness |
| Extra classes | `JournaledStateWriter` + provider | none |
| Per-stage cursor | yes | yes |
| Atomic | yes | yes |
