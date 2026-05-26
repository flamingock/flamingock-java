# Error reporting proposal — rich execution report via event listener

**Status:** implemented (2026-05-25).
- **Step 1 (single-line `getMessage()` summary) — DONE.** `StagedExecuteOperationException.getMessage()` delegates to `ExecutionReportFormatter.summary(...)`. MI change IDs are appended when any stage is blocked.
- **Step 2 (exception is one-line only; no `toString()` override) — DONE.** Originally we shipped a `toString()` override returning the multi-line report; real-world testing in Spring Boot showed `printStackTrace` then duplicated the listener's output. Reverted: both `StagedExecuteOperationException` and `PipelineExecuteOperationException` use `Throwable`'s default `toString()` (= `ClassName: getMessage()`). Multi-line rendering is exclusively the listener's job.
- **Step 3 (default event listener writing via SLF4J + builder opt-out) — DONE.** Two separate listeners — `DefaultPipelineCompletedReportListener` (INFO) and `DefaultPipelineFailedReportListener` (ERROR) — both writing under the `FK-Report` logger (constant `LOGGER_NAME = "Report"`; `FlamingockLoggerFactory` prepends `FK-` automatically). Composed with any user listener via `Consumer.andThen` when `enableDefaultExecutionReport(true)` (default). Inline summary `logger.info(...)` calls in `AbstractPipelineTraverseOperation` removed in favor of the listener.
- **Counter semantics.** `ChangeStatus.ROLLED_BACK` is widened into the `failedChanges` aggregate (in `PipelineRun.toResponse()`) and into the per-stage `failed` count + `failed change(s):` line (in `ExecutionReportFormatter`). The precise `ROLLED_BACK` status remains on each `ChangeResult` for consumers that need the distinction.
- **Two-tier reporting: reached vs total (2026-05-25).** The report now separates structural counts (`totalStages`, `totalChanges` — from the loaded pipeline) from observational counts (`reachedStages`, `reachedChanges` — the executor was invoked on this stage). The per-stage breakdown shows only reached stages (`wasExecuted=true` on `StageResult`); unreached stages appear in a separate "Not reached" section when the coverage is partial. When nothing is reached and nothing failed, status is `ExecutionStatus.NO_CHANGES` and the headline reads `Flamingock execution report — NO CHANGES`. Reachedness is flipped exactly once via `PipelineRun.markStageReached(...)` at the entry of `AbstractPipelineTraverseOperation.runStage`. This closes both the run-2-shows-zeros bug and the cross-edition `[PENDING]` vs `[COMPLETED]` divergence (community's synthetic-completion path and cloud's CONTINUE path both leave `wasExecuted=false`, so both produce the same `NO CHANGES` report for an up-to-date run).

**Related code:** `core/flamingock-core/.../operation/AbstractPipelineTraverseOperation.java`, `ExecuteOperationException` / `StagedExecuteOperationException` / `PipelineExecuteOperationException`, `core/flamingock-core-commons/.../response/data/{ExecuteResponseData,ExecutionReportFormatter}.java`, `core/flamingock-core/.../event/listener/`, `core/flamingock-core/.../builder/AbstractChangeRunnerBuilder.java`, `core/flamingock-core/.../configuration/core/CoreConfiguration.java`. Spring Boot wrappers under `platform-plugins/flamingock-springboot-integration/.../event/` pass `getResult()` through (and the four `SpringStage*Event` classes were re-typed to implement their correct `IStage*Event` interfaces — they were previously cross-wired to `IPipeline*Event`).

## Context

After Phase 3 of removing fail-fast made stage execution independent, the typed exception hierarchy (`ExecuteOperationException` and its `Staged…` / `Pipeline…` subclasses) carries `ExecuteResponseData` through to callers. The information is there, but the *default rendering* is poor.

Today, when the runner fails, the log shows:

```
io.flamingock.internal.core.operation.StagedExecuteOperationException: One or more stages failed during execution
    at io.flamingock.internal.core.operation.AbstractPipelineTraverseOperation.execute(...)
    [stack frames…]
```

A developer reading this gets only the class name and a bland one-liner. The structured per-stage failure data (status counts, duration, per-stage error info) is in `getResult()` but invisible unless the consumer explicitly extracts it.

We want a richer default output without hurting enterprise log pipelines.

## The trade-off space

Three positions exist, each with documented precedent:

1. **Terse `getMessage()` + structured getter** — pattern used by `jakarta.validation.ConstraintViolationException`. The exception is a clean signal; callers iterate `getConstraintViolations()` themselves. Aggregator-friendly. Costs: developers don't see the rich info on `e.printStackTrace()` / default `logger.error(..., e)`. Worst onboarding experience.

2. **Rich `getMessage()`** — pattern used by Spring's `BindException` / `MethodArgumentNotValidException`. `getMessage()` builds a multi-line summary of all field errors. Developers see everything automatically. Costs: log-aggregator fragmentation in any team whose Splunk / Datadog / Filebeat lacks a multi-line pattern. Spring users in enterprise have learned to deal with this; it's a known wart.

3. **`toString()` override + terse `getMessage()`** — less common but technically clean. JDK's `printStackTrace()` uses `toString()` for the headline (so IDEs / dev terminals show the rich report). Logback's `%ex` and most JSON encoders use `getMessage()` (so log aggregators see the one-liner). Costs: subtle invariant; future maintainers may not understand why `toString()` differs from `getMessage()`; some consumers do `logger.error("X: " + ex)` (concatenation) which calls `toString()` and lands the rich content in log fields.

Spring and Hibernate Validator disagree on which is "right". Both are widely deployed.

## Proposed approach — beyond Spring/Hibernate

Separate the *signal* (exception) from the *rendering* (event listener):

- `getMessage()` stays a **single line** with the count + failed stage names. Example: `"Flamingock execution failed: 1 stage(s) failed [flamingock-system-stage]"`. Enterprise-aggregator friendly.
- `toString()` returns the **rich multi-line report** as a safety net for ad-hoc usage (`System.err.println(ex)`, debuggers, REPL). Not the primary path.
- Default rendering happens **via an event listener** registered by the builder. The listener subscribes to `PipelineFailedEvent` / `PipelineCompletedEvent` and writes the rich report via SLF4J under a dedicated logger name (e.g., `FK-Report`). Two layers of control: a builder flag for full-stop disable, plus standard SLF4J config (`<logger name="FK-Report" level="OFF"/>`) for fine-grained silencing without rebuild.

This gives:
- Default users see a rich, useful report out of the box (better than Spring/Hibernate).
- Enterprises silence it via standard logging configuration — no library code change needed.
- The same data is available to *any* consumer (CLI, dashboards, Cloud Edition, custom Slack/metrics listeners) through the existing event system. No format is hardcoded; everything is composable.
- The exception remains a clean typed signal carrying structured data.

## Design decisions to lock down

### Event payloads carry structured data directly

`PipelineFailedEvent` and `PipelineCompletedEvent` should carry `ExecuteResponseData` as a typed field — *not* require listeners to downcast a `Throwable` to extract it. Events become self-describing. Per-stage events (`StageFailedEvent`, `StageCompletedEvent`) carry `StageResult` for symmetry.

### Default listener writes via SLF4J

A dedicated logger name (e.g., `FK-Report`) at:
- `INFO` for `PipelineCompletedEvent`.
- `ERROR` for `PipelineFailedEvent`.

Two layers of control:
1. Builder flag `enableDefaultExecutionReport(boolean)` — default `true`. Full opt-out.
2. SLF4J config — `<logger name="FK-Report" level="OFF"/>` silences without rebuild.

### Consolidate today's ad-hoc summary logs into the listener

Today `AbstractPipelineTraverseOperation` inlines:
- `logger.info("Flamingock execution completed [duration=Xms applied=N skipped=M]")`
- `logger.info("Flamingock execution finished with stage failures [duration=Xms applied=N skipped=M failed=K]")`

These should move into the default listener so there is a single source of truth for "what gets logged about a run." Otherwise we'll double-log once the listener exists.

### Formatter as a reusable utility

A static `ExecutionReportFormatter` (in `flamingock-core-commons`, next to `ExecuteResponseData`) produces the multi-line report from response data. Used by:
- The default listener (rendering for log output).
- `ExecuteOperationException.toString()` (rendering for the safety-net path).
- Any third-party consumer who wants the same canonical rendering.

### Report shape (proposed)

```
<headline: N stage(s) failed (or pipeline-wide error)>

Summary:
  Status:      FAILED
  Duration:    312ms (2026-05-15T08:37:56.224 → 2026-05-15T08:37:56.536)
  Stages:      1 failed, 2 completed (3 total)
  Changes:     3 applied, 0 skipped, 1 failed (4 total)

Failed stages:
  - flamingock-system-stage [InvalidConfigurationException]
      Invalid value for internal.mongock.import.skip: invalid_value (expected "true" or "false" or empty)
      Changes: migration-mongock-to-flamingock-community
```

For `BlockedForMI` stages: distinct label (`BLOCKED — manual intervention required`) plus the change IDs from `recoveryIssues` instead of `errorInfo.changeIds`. The CLI's existing `ExecutionResultFormatter` stays separate (it's for terminal output with decorative banners; this report is for log streams).

### Defensive listener

The default listener must **never throw**. If formatting fails, fall back to a one-line error log and continue. A misbehaving renderer should never mask the real underlying error or break event propagation.

## Risks and footguns

- **Test output noise.** Every test that triggers a failure will surface a rich report in test logs. Test infrastructure (`InternalInMemoryTestKit`, etc.) may want to disable the default listener by default for unit tests to keep output clean.
- **Plugin event publishers.** Some integration plugins (e.g. Spring Boot) contribute their own event publishers. Our default listener registers against the *core* publisher. Worth verifying no duplicate firing if a plugin re-publishes core events.
- **Order of operations on failure.** With the listener writing via SLF4J synchronously, report log lines appear *before* the consumer's "Flamingock failed" log line in their own catch block. Time-ordered in any sink — not a bug, but worth being explicit about so it's not surprising.
- **Double-rendering if consumers add their own listener.** Listeners are additive by default. A consumer who registers a custom listener AND leaves the default enabled gets both outputs. Accept additive behavior; document the opt-out flag.
- **`toString()` ≠ `getMessage()`** is a subtle invariant. A future maintainer might "fix" them to match. Add a code comment near the override explaining why they differ and pointing at this doc.
- **Plain-text legacy log pipelines.** Even with `getMessage()` terse, the default listener still writes multi-line via SLF4J. Enterprises with unconfigured multi-line patterns will still see fragmentation. The SLF4J-level opt-out (`level="OFF"` on the `FK-Report` logger) addresses this without disabling the whole listener system.
- **`PipelineFailedEvent` with neither stages nor cause.** Defensive case (shouldn't happen but). Listener handles gracefully — print whatever's available, never throw.

## Why this is better than Spring / Hibernate

Spring puts everything into `getMessage()` and accepts the aggregator pain — that's a one-size-fits-all decision. Hibernate Validator puts nothing into the message and accepts the developer-onboarding pain — also one-size-fits-all. Neither library decouples the data from its rendering.

By routing default rendering through the event system:
- The exception stays a clean typed signal (Hibernate's strength).
- Developers see rich output by default (Spring's strength).
- Enterprises silence the rich path via standard logging config (neither framework offers this cleanly).
- Third parties compose new output formats (Slack, metrics, JSON, anything) without library code changes (neither framework offers this).

The listener pattern *replaces* the inline `logger.info(...)` calls in the operation, so we don't end up with two reporting paths.

## Implementation scope (when picked up)

Rough sketch — not a plan, just sizing:

1. Update event payloads — `PipelineFailedEvent` / `PipelineCompletedEvent` carry `ExecuteResponseData`; `StageFailedEvent` / `StageCompletedEvent` carry `StageResult`.
2. Add `ExecutionReportFormatter` in `flamingock-core-commons`.
3. Override `toString()` on `ExecuteOperationException` to call the formatter.
4. Keep `getMessage()` terse — include failed stage names so log-line triage works.
5. Add `DefaultExecutionReportListener` in `flamingock-core/.../event/listener/`. Listens to `PipelineFailedEvent` / `PipelineCompletedEvent`. Writes via SLF4J under `FK-Report`. Defensive (never throws).
6. Register the listener by default in `AbstractFlamingockBuilder.build()` unless disabled.
7. Add `enableDefaultExecutionReport(boolean)` to the builder (default `true`).
8. Remove the inline summary `logger.info(...)` calls from `AbstractPipelineTraverseOperation` (replaced by the listener).
9. Test-kit defaults — consider disabling the default listener in test kits for clean test output.
10. Tests — formatter rendering snapshot; listener fires-once and writes-expected-content; builder flag opt-out works; defensive case (listener doesn't throw on bad data).

## Open questions

- Should `PipelineCompletedEvent` always get a full report, or only a one-line summary when there are no failures? (Symmetry argument: same listener for both, just different log levels; rendering can be conditional.)
- Should the formatter be open for extension (themes, locales) or stay closed and reused as-is by anyone who wants different formatting (they write their own listener)? Leaning closed — the canonical rendering is a contract; bespoke formats live in bespoke listeners.
- Default behaviour in test kits: opt-out by default, or opt-in via test flag? Likely opt-out by default to keep CI logs clean.
