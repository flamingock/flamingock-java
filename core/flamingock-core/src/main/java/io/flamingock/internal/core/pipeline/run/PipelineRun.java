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
import io.flamingock.internal.core.pipeline.execution.StageExecutionException;
import io.flamingock.internal.core.pipeline.loaded.LoadedPipeline;
import io.flamingock.internal.core.pipeline.loaded.stage.AbstractLoadedStage;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
        // the runtime entry point so callers don't have to validate separately.
        pipeline.validate();
        List<AbstractLoadedStage> stages = new ArrayList<>();
        pipeline.getSystemStage().ifPresent(stages::add);
        stages.addAll(pipeline.getStages());
        return of(stages);
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
        // Incoming result from the executor already carries state=COMPLETED. Preserve the
        // totalChanges / plannerVerdict fields set on the in-memory StageResult before
        // execution started — the executor doesn't know about them and would otherwise reset
        // them to defaults.
        StageRun run = lookupOrThrow(stageName);
        StageResult current = run.getResult();
        run.setResult(StageResult.builder(result)
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
        run.setResult(StageResult.builder(resultFromExecutor)
                .state(StageState.failed(errorInfo))
                .totalChanges(current.getTotalChanges())
                .plannerVerdict(current.getPlannerVerdict())
                .build());
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
     * Adds {@link ChangeStatus#ALREADY_APPLIED} {@link ChangeResult}s for the given change IDs.
     * Defensive merge: for any change ID the operation has already recorded a result for, the
     * planner's would-be record is dropped — the operation's observation is more authoritative.
     * This keeps the iterative planner→operation→planner cycle safe when audit re-reads see
     * APPLIED entries the operation just wrote.
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
        Set<String> existing = new HashSet<>();
        for (ChangeResult c : merged) {
            if (c != null && c.getChangeId() != null) {
                existing.add(c.getChangeId());
            }
        }
        for (String id : changeIds) {
            if (id == null || existing.contains(id)) {
                continue;
            }
            merged.add(ChangeResult.builder()
                    .changeId(id)
                    .status(ChangeStatus.ALREADY_APPLIED)
                    .build());
            existing.add(id);
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
        int skippedChanges = 0;
        int failedChanges = 0;
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
                        skippedChanges++;
                    } else if (change.getStatus() == ChangeStatus.FAILED
                            || change.getStatus() == ChangeStatus.ROLLED_BACK) {
                        // ROLLED_BACK counts as a failed attempt for the user-facing aggregate.
                        // The per-change record retains the precise status for downstream consumers.
                        failedChanges++;
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
                .skippedChanges(skippedChanges)
                .failedChanges(failedChanges)
                .stages(stages)
                .build();
    }
}
