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
package io.flamingock.internal.core.pipeline.run;

import io.flamingock.api.StageType;
import io.flamingock.internal.common.core.recovery.RecoveryIssue;
import io.flamingock.internal.common.core.response.data.ChangeResult;
import io.flamingock.internal.common.core.response.data.ChangeStatus;
import io.flamingock.internal.common.core.response.data.ErrorInfo;
import io.flamingock.internal.common.core.response.data.ExecuteResponseData;
import io.flamingock.internal.common.core.response.data.ExecutionStatus;
import io.flamingock.internal.common.core.response.data.PlannerVerdict;
import io.flamingock.internal.common.core.response.data.StageResult;
import io.flamingock.internal.common.core.response.data.StageState;
import io.flamingock.internal.core.change.filter.ChangeFilter;
import io.flamingock.internal.core.change.loaded.AbstractLoadedChange;
import io.flamingock.internal.core.pipeline.execution.StageExecutionException;
import io.flamingock.internal.core.pipeline.loaded.LoadedPipeline;
import io.flamingock.internal.core.pipeline.loaded.stage.AbstractLoadedStage;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class PipelineRun {

    /**
     * Block-dependency ordering: every stage in an earlier block must succeed before the next
     * block may execute. SYSTEM is the foundation, LEGACY runs second, DEFAULT (user) runs last.
     */
    private static final List<StageType> BLOCK_ORDER =
            Arrays.asList(StageType.SYSTEM, StageType.LEGACY, StageType.DEFAULT);

    public static PipelineRun of(LoadedPipeline pipeline) {
        // Static-structure validation runs as part of construction. Builder-time validation in
        // AbstractChangeRunnerBuilder still fails fast on malformed pipelines; this call covers
        // the runtime entry point so callers don't have to validate separately. Validation
        // operates on the unfiltered pipeline so structural checks (duplicate IDs, empty
        // stages, etc.) catch issues regardless of which changes any plugin would exclude.
        pipeline.validate();
        Collection<ChangeFilter> filters = pipeline.getChangeFilters();
        List<AbstractLoadedStage> stages = new ArrayList<>();
        // Apply the same drop-empty rule to the system stage as to user stages. A stage is
        // dropped only when filtering actually removed every change from a stage that had
        // some to begin with. A stage that was already empty before filtering is preserved
        // unchanged; this matches the pre-existing "empty stages survive" contract used by
        // builder-mocked tests and by incremental compile-time flows where a stage may be
        // temporarily empty before any @Change has been added to its package.
        pipeline.getSystemStage()
                .map(stage -> applyFilters(stage, filters))
                .filter(result -> !result.filteredToEmpty)
                .map(result -> result.stage)
                .ifPresent(stages::add);
        for (AbstractLoadedStage stage : pipeline.getStages()) {
            FilterResult result = applyFilters(stage, filters);
            if (!result.filteredToEmpty) {
                stages.add(result.stage);
            }
        }
        return of(stages);
    }

    /**
     * Result of running change filters against a single stage. {@code stage} is either the
     * original stage (no filter removed any change, OR the original was empty, OR no filters
     * were provided) or a new instance with the surviving changes. {@code filteredToEmpty}
     * is {@code true} only when the filter actually removed every change from a non-empty
     * stage — that's the signal the caller uses to drop the stage from the runtime list.
     */
    private static final class FilterResult {
        final AbstractLoadedStage stage;
        final boolean filteredToEmpty;

        FilterResult(AbstractLoadedStage stage, boolean filteredToEmpty) {
            this.stage = stage;
            this.filteredToEmpty = filteredToEmpty;
        }
    }

    /**
     * Applies the given change filters to a stage's changes, returning a {@link FilterResult}
     * that preserves the original stage reference when no filter removed any change. Filters
     * are AND-composed: a change is kept only if every filter returns {@code true} for it.
     */
    private static FilterResult applyFilters(AbstractLoadedStage stage, Collection<ChangeFilter> filters) {
        Collection<AbstractLoadedChange> originalChanges = stage.getChanges();
        if (filters == null || filters.isEmpty() || originalChanges == null || originalChanges.isEmpty()) {
            // No filtering meaningfully applied. Preserve the original stage unchanged — this
            // includes the case of a pre-existing empty stage (filteredToEmpty=false).
            return new FilterResult(stage, false);
        }
        Collection<AbstractLoadedChange> surviving = originalChanges.stream()
                .filter(change -> filters.stream().allMatch(f -> f.filter(change)))
                .collect(Collectors.toList());
        if (surviving.size() == originalChanges.size()) {
            return new FilterResult(stage, false);
        }
        return new FilterResult(stage.withChanges(surviving), surviving.isEmpty());
    }

    public static PipelineRun of(List<AbstractLoadedStage> stages) {
        // Partition by StageType in dependency order, skipping empty types (sparse). The resulting
        // flat list is the concatenation of blocks in canonical order — a single source of truth
        // for stage ordering across the rest of the runtime.
        Map<StageType, List<StageRun>> byType = new LinkedHashMap<>();
        for (StageType type : BLOCK_ORDER) {
            byType.put(type, new ArrayList<>());
        }
        for (AbstractLoadedStage stage : stages) {
            byType.get(resolveType(stage)).add(new StageRun(stage));
        }

        List<StageRunBlock> blocks = new ArrayList<>();
        List<StageRun> flat = new ArrayList<>();
        for (StageType type : BLOCK_ORDER) {
            List<StageRun> blockRuns = byType.get(type);
            if (!blockRuns.isEmpty()) {
                blocks.add(new StageRunBlock(type, blockRuns));
                flat.addAll(blockRuns);
            }
        }
        return new PipelineRun(flat, blocks);
    }

    /**
     * Defensive null-handling: production stages always carry a {@link StageType}; legacy test
     * mocks may not stub {@code getType()} and yield null. Treat null as DEFAULT (the most common
     * type, what a normal user stage would be).
     */
    private static StageType resolveType(AbstractLoadedStage stage) {
        StageType type = stage.getType();
        return type != null ? type : StageType.DEFAULT;
    }

    private final List<StageRun> stageRuns;
    private final List<StageRunBlock> stageBlocks;
    private final Map<String, StageRun> byName;
    private Instant startedAt;
    private Instant stoppedAt;

    private PipelineRun(List<StageRun> stageRuns, List<StageRunBlock> stageBlocks) {
        this.stageRuns = Collections.unmodifiableList(stageRuns);
        this.stageBlocks = Collections.unmodifiableList(stageBlocks);
        Map<String, StageRun> index = new LinkedHashMap<>();
        for (StageRun run : stageRuns) {
            index.put(run.getName(), run);
        }
        this.byName = Collections.unmodifiableMap(index);
    }

    public List<StageRun> getStageRuns() {
        return stageRuns;
    }

    public StageRun getStageRun(String name) {
        return byName.get(name);
    }

    /**
     * Stages grouped into typed blocks in dependency order. Empty blocks are omitted (sparse): if
     * a pipeline has no SYSTEM stages, no SYSTEM block is returned. Consumers treat the list as
     * an ordered execution dependency — every stage in {@code blocks[i]} must complete
     * successfully before any stage in {@code blocks[i+1]} may run.
     */
    public List<StageRunBlock> getStageBlocks() {
        return stageBlocks;
    }

    /** Number of stages in this run, across all blocks. */
    public int getStageCount() {
        return stageRuns.size();
    }

    /** Total number of changes across all stages. */
    public long getTotalChangeCount() {
        long count = 0;
        for (StageRun stageRun : stageRuns) {
            count += stageRun.getLoadedStage().getChanges().size();
        }
        return count;
    }

    public List<AbstractLoadedStage> getLoadedStages() {
        return stageRuns.stream()
                .map(StageRun::getLoadedStage)
                .collect(Collectors.toList());
    }

    public void start() {
        this.startedAt = Instant.now();
    }

    public void stop() {
        this.stoppedAt = Instant.now();
    }

    public void markStageStarted(String stageName) {
        StageRun run = lookupOrThrow(stageName);
        run.setResult(StageResult.builder(run.getResult())
                .state(StageState.STARTED)
                .build());
    }

    public void markStageCompleted(String stageName, StageResult result) {
        // Incoming result from the executor already carries state=COMPLETED. Merge per-change
        // records by ID: the executor's records win for the IDs it processed; records for
        // changes the executor didn't process (i.e. NOT_REACHED records from PipelineRun
        // construction) survive. Also preserve totalChanges / plannerVerdict — the executor
        // doesn't know about them.
        StageRun run = lookupOrThrow(stageName);
        StageResult current = run.getResult();
        List<ChangeResult> merged = mergeChangeRecords(current.getChanges(), result.getChanges());
        run.setResult(StageResult.builder(result)
                .changes(merged)
                .totalChanges(current.getTotalChanges())
                .plannerVerdict(current.getPlannerVerdict())
                .build());
    }

    public void markStageFailed(String stageName, StageExecutionException exception) {
        StageResult resultFromExecutor = exception.getResult();
        ErrorInfo errorInfo = ErrorInfo.fromThrowable(
                exception.getCause(),
                Collections.singletonList(exception.getFailedChangeId()),
                resultFromExecutor.getStageId()
        );
        StageRun run = lookupOrThrow(stageName);
        StageResult current = run.getResult();
        // Same merge semantics as markStageCompleted: executor's records win for processed
        // IDs; NOT_REACHED records for changes the executor never reached (stage stopped on
        // a failure) stay intact.
        List<ChangeResult> merged = mergeChangeRecords(current.getChanges(), resultFromExecutor.getChanges());
        run.setResult(StageResult.builder(resultFromExecutor)
                .state(StageState.failed(errorInfo))
                .changes(merged)
                .totalChanges(current.getTotalChanges())
                .plannerVerdict(current.getPlannerVerdict())
                .build());
    }

    /**
     * Merges two per-change record lists keyed by change ID. Incoming records (typically from
     * the executor) take precedence for the IDs they carry; existing records (e.g. initial
     * NOT_REACHED entries from {@code StageRun} construction) for any other ID survive. Order
     * follows the first occurrence — existing entries keep their position; new IDs from
     * {@code incoming} are appended.
     */
    private static List<ChangeResult> mergeChangeRecords(List<ChangeResult> existing,
                                                          List<ChangeResult> incoming) {
        LinkedHashMap<String, ChangeResult> byId = new LinkedHashMap<>();
        if (existing != null) {
            for (ChangeResult r : existing) {
                if (r != null && r.getChangeId() != null) {
                    byId.put(r.getChangeId(), r);
                }
            }
        }
        if (incoming != null) {
            for (ChangeResult r : incoming) {
                if (r != null && r.getChangeId() != null) {
                    byId.put(r.getChangeId(), r);
                }
            }
        }
        return new ArrayList<>(byId.values());
    }

    /**
     * Stage-scope failure for non-{@link StageExecutionException} Throwables (no enriched
     * {@link StageResult} from the executor; we rebuild the existing one with a Failed state).
     */
    public void markStageFailed(String stageName, Throwable cause) {
        StageRun run = lookupOrThrow(stageName);
        ErrorInfo errorInfo = ErrorInfo.fromThrowable(cause, Collections.emptyList(), stageName);
        run.setResult(StageResult.builder(run.getResult())
                .state(StageState.failed(errorInfo))
                .build());
    }

    /**
     * Marks a single stage as blocked for manual intervention with its attributed recovery
     * issues. Per-stage attribution: each stage owns its own MI state, so a single stage's
     * BlockedForMI does not affect the rest of the pipeline.
     */
    public void markStageBlockedFromMI(String stageName, List<RecoveryIssue> issues) {
        StageRun run = lookupOrThrow(stageName);
        run.setResult(StageResult.builder(run.getResult())
                .state(StageState.blockedManualIntervention(stageName, issues))
                .build());
    }

    // -------------------------------------------------------------------------
    //  Planner-side writers
    // -------------------------------------------------------------------------
    //
    //  These methods are owned by the planner — they reflect facts the planner reads from audit
    //  (community) or the cloud server. Conflict resolution rule: operation wins, planner fills
    //  gaps. Both methods enforce that here so callers don't have to be careful.

    /**
     * Records the planner's verdict on a stage. Monotone forward through the chain
     * NOT_EVALUATED → NEEDS_WORK → UP_TO_DATE:
     * <ul>
     *   <li>NOT_EVALUATED → anything: always allowed.</li>
     *   <li>NEEDS_WORK → UP_TO_DATE: allowed (subsequent snapshot reads see new applies).</li>
     *   <li>UP_TO_DATE → anything: rejected (UP_TO_DATE is terminal).</li>
     *   <li>Any → NOT_EVALUATED: rejected (never move back to default).</li>
     * </ul>
     * Designed to be safe across the iterative {@code getNextExecution} loop where the planner
     * re-walks the snapshot each iteration.
     */
    public void markStageVerdict(String stageName, PlannerVerdict verdict) {
        if (verdict == null || verdict == PlannerVerdict.NOT_EVALUATED) {
            return; // monotone-forward; never move back to default
        }
        StageRun run = lookupOrThrow(stageName);
        StageResult current = run.getResult();
        PlannerVerdict currentVerdict = current.getPlannerVerdict();
        if (currentVerdict == PlannerVerdict.UP_TO_DATE) {
            return; // UP_TO_DATE is terminal — no further transitions
        }
        if (currentVerdict == PlannerVerdict.NEEDS_WORK && verdict == PlannerVerdict.NEEDS_WORK) {
            return; // no-op
        }
        run.setResult(StageResult.builder(current)
                .plannerVerdict(verdict)
                .build());
    }

    /**
     * Upgrades {@link ChangeStatus#NOT_REACHED} records to {@link ChangeStatus#ALREADY_APPLIED}
     * for the given change IDs. Defensive merge: only upgrades records currently at NOT_REACHED;
     * any record the operation (or a prior writer) has already populated with positive info
     * (APPLIED / FAILED / ROLLED_BACK / ALREADY_APPLIED) is left untouched.
     *
     * <p>This codifies "operation wins, planner fills gaps" against the fully-populated record
     * set that {@code StageRun} initializes with NOT_REACHED entries. As a fallback, IDs without
     * an existing record (shouldn't happen — every loaded change starts with one — but defensive)
     * get appended as fresh ALREADY_APPLIED.
     */
    public void markStageAlreadyAppliedFromAudit(String stageName, List<String> changeIds) {
        if (changeIds == null || changeIds.isEmpty()) {
            return;
        }
        StageRun run = lookupOrThrow(stageName);
        StageResult current = run.getResult();
        List<ChangeResult> merged = current.getChanges() != null
                ? new ArrayList<>(current.getChanges())
                : new ArrayList<>();
        Map<String, Integer> indexById = new HashMap<>();
        for (int i = 0; i < merged.size(); i++) {
            ChangeResult c = merged.get(i);
            if (c != null && c.getChangeId() != null) {
                indexById.put(c.getChangeId(), i);
            }
        }
        for (String id : changeIds) {
            if (id == null) {
                continue;
            }
            Integer idx = indexById.get(id);
            if (idx == null) {
                // No existing record (defensive): append.
                merged.add(ChangeResult.builder()
                        .changeId(id)
                        .status(ChangeStatus.ALREADY_APPLIED)
                        .build());
                indexById.put(id, merged.size() - 1);
                continue;
            }
            if (merged.get(idx).isNotReached()) {
                merged.set(idx, ChangeResult.builder()
                        .changeId(id)
                        .status(ChangeStatus.ALREADY_APPLIED)
                        .build());
            }
            // else: operation (or prior planner write) has positive info — respect it.
        }
        run.setResult(StageResult.builder(current)
                .changes(merged)
                .build());
    }

    private StageRun lookupOrThrow(String stageName) {
        StageRun run = byName.get(stageName);
        if (run == null) {
            throw new IllegalArgumentException("Unknown stage: " + stageName);
        }
        return run;
    }

    /**
     * Builds the response data view from per-stage state.
     *
     * <p>Per-stage buckets are derived from the two-dimensional model:
     * <ul>
     *   <li>{@code completedStages} / {@code failedStages} — execution dimension (operation-owned).</li>
     *   <li>{@code upToDateStages} — verdict dimension (planner confirmed up to date while state
     *       was still NOT_STARTED).</li>
     *   <li>{@code notReachedStages} — remaining stages (NOT_STARTED + no UP_TO_DATE verdict).</li>
     * </ul>
     *
     * <p>Status is {@code FAILED} iff any stage failed, {@code NO_CHANGES} iff no work happened
     * (no applies, no failures), else {@code SUCCESS}. Pipeline-wide errors (e.g., LockException)
     * are signalled by the operation post-loop via {@code ExecuteResponseData.setStatus(FAILED)} after
     * {@code toResponse()} returns.
     */
    public ExecuteResponseData toResponse() {
        List<StageResult> stages = new ArrayList<>();
        int totalStages = 0;
        int upToDateStages = 0;
        int notReachedStages = 0;
        int completedStages = 0;
        int failedStages = 0;
        int totalChanges = 0;
        int appliedChanges = 0;
        int alreadyAppliedChanges = 0;
        int failedChanges = 0;
        int notReachedChanges = 0;
        boolean anyFailed = false;

        for (StageRun stageRun : stageRuns) {
            StageResult stageResult = stageRun.getResult();
            // Stamp the structural change count on the per-stage result so the formatter can
            // render "(N changes)" for unreached / up-to-date stages where the per-change list
            // may be empty.
            int loadedChangeCount = stageRun.getLoadedStage().getChanges() != null
                    ? stageRun.getLoadedStage().getChanges().size()
                    : 0;
            stageResult.setTotalChanges(loadedChangeCount);
            stages.add(stageResult);
            totalStages++;
            totalChanges += loadedChangeCount;

            // Per-stage bucketing: state is operation-owned, plannerVerdict is planner-owned.
            // Operation wins when present; planner verdict applies only while state is NOT_STARTED.
            StageState state = stageResult.getState();
            if (state.isFailed()) {
                failedStages++;
                anyFailed = true;
            } else if (state.isCompleted()) {
                completedStages++;
            } else if (state.isNotStarted()
                    && stageResult.getPlannerVerdict() == PlannerVerdict.UP_TO_DATE) {
                upToDateStages++;
            } else {
                // NOT_STARTED + verdict != UP_TO_DATE (NEEDS_WORK or NOT_EVALUATED), or rare STARTED.
                notReachedStages++;
            }

            if (stageResult.getChanges() != null) {
                for (ChangeResult change : stageResult.getChanges()) {
                    if (change.getStatus() == ChangeStatus.APPLIED) {
                        appliedChanges++;
                    } else if (change.getStatus() == ChangeStatus.ALREADY_APPLIED) {
                        alreadyAppliedChanges++;
                    } else if (change.getStatus() == ChangeStatus.FAILED
                            || change.getStatus() == ChangeStatus.ROLLED_BACK) {
                        // ROLLED_BACK counts as a failed attempt for the user-facing aggregate.
                        // The per-change record retains the precise status for downstream consumers.
                        failedChanges++;
                    } else if (change.getStatus() == ChangeStatus.NOT_REACHED) {
                        notReachedChanges++;
                    }
                }
            }
        }

        ExecutionStatus status;
        if (anyFailed) {
            status = ExecutionStatus.FAILED;
        } else if (appliedChanges == 0 && failedChanges == 0) {
            // No work happened (no applies, no failures). Could be: every stage was up to date,
            // or pipeline was empty. Either way → NO_CHANGES for the headline.
            status = ExecutionStatus.NO_CHANGES;
        } else {
            status = ExecutionStatus.SUCCESS;
        }

        long durationMs = (startedAt != null && stoppedAt != null)
                ? Duration.between(startedAt, stoppedAt).toMillis()
                : 0L;

        return ExecuteResponseData.builder()
                .status(status)
                .startTime(startedAt)
                .endTime(stoppedAt)
                .totalDurationMs(durationMs)
                .totalStages(totalStages)
                .upToDateStages(upToDateStages)
                .notReachedStages(notReachedStages)
                .completedStages(completedStages)
                .failedStages(failedStages)
                .totalChanges(totalChanges)
                .appliedChanges(appliedChanges)
                .alreadyAppliedChanges(alreadyAppliedChanges)
                .failedChanges(failedChanges)
                .notReachedChanges(notReachedChanges)
                .stages(stages)
                .build();
    }
}
