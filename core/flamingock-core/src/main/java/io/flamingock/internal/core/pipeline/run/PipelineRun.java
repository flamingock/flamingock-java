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

    /**
     * Marks a stage as "reached" — the executor was invoked on it. Called from the operation
     * layer at the very entry of {@code runStage}. Drives the reporter's "reached vs total"
     * split: stages where the planner short-circuited (e.g. synthetic-completion in community,
     * CONTINUE in cloud) keep {@code wasExecuted=false} and are excluded from the per-stage
     * breakdown.
     */
    public void markStageReached(String stageName) {
        StageRun run = lookupOrThrow(stageName);
        run.setResult(StageResult.builder(run.getResult())
                .wasExecuted(true)
                .build());
    }

    public void markStageCompleted(String stageName, StageResult result) {
        // Incoming result from the executor already carries state=COMPLETED. Preserve the
        // wasExecuted / totalChanges fields that were set on the in-memory StageResult before
        // execution started — the executor doesn't know about them and would otherwise reset
        // them to defaults.
        StageRun run = lookupOrThrow(stageName);
        StageResult current = run.getResult();
        run.setResult(StageResult.builder(result)
                .wasExecuted(current.isWasExecuted())
                .totalChanges(current.getTotalChanges())
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
                .wasExecuted(current.isWasExecuted())
                .totalChanges(current.getTotalChanges())
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

    private StageRun lookupOrThrow(String stageName) {
        StageRun run = byName.get(stageName);
        if (run == null) {
            throw new IllegalArgumentException("Unknown stage: " + stageName);
        }
        return run;
    }

    /**
     * Builds the response data view derived from per-stage state.
     *
     * <p>Two counter dimensions: {@code total*} comes from the loaded pipeline (structural,
     * always known), {@code reached*} comes from this run (the executor was invoked on the
     * stage). The {@code applied/skipped/failed} buckets only count changes from reached stages
     * — by construction {@code applied + skipped + failed == reachedChanges}.
     *
     * <p>Status is {@code FAILED} iff any stage failed, {@code NO_CHANGES} iff nothing was
     * reached and nothing failed (e.g. a re-run where the audit already covers every change),
     * else {@code SUCCESS}. Pipeline-wide errors (e.g., LockException) are signalled by the
     * operation post-loop via {@code ExecuteResponseData.setStatus(FAILED)} after
     * {@code toResponse()} returns.
     */
    public ExecuteResponseData toResponse() {
        List<StageResult> stages = new ArrayList<>();
        int totalStages = 0;
        int reachedStages = 0;
        int completedStages = 0;
        int failedStages = 0;
        int totalChanges = 0;
        int reachedChanges = 0;
        int appliedChanges = 0;
        int skippedChanges = 0;
        int failedChanges = 0;
        boolean anyFailed = false;

        for (StageRun stageRun : stageRuns) {
            StageResult stageResult = stageRun.getResult();
            // Stamp the structural change count on the per-stage result so the formatter can
            // render "(N changes)" for unreached stages where the per-change list is empty.
            int loadedChangeCount = stageRun.getLoadedStage().getChanges() != null
                    ? stageRun.getLoadedStage().getChanges().size()
                    : 0;
            stageResult.setTotalChanges(loadedChangeCount);
            stages.add(stageResult);
            totalStages++;
            totalChanges += loadedChangeCount;

            if (stageResult.isWasExecuted()) {
                reachedStages++;
                if (stageResult.getChanges() != null) {
                    reachedChanges += stageResult.getChanges().size();
                }
            }

            StageState state = stageResult.getState();
            if (state.isCompleted()) {
                completedStages++;
            } else if (state.isFailed()) {
                failedStages++;
                anyFailed = true;
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
        } else if (reachedChanges == 0) {
            // Nothing was touched and nothing failed — pipeline is up to date.
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
                .reachedStages(reachedStages)
                .completedStages(completedStages)
                .failedStages(failedStages)
                .totalChanges(totalChanges)
                .reachedChanges(reachedChanges)
                .appliedChanges(appliedChanges)
                .skippedChanges(skippedChanges)
                .failedChanges(failedChanges)
                .stages(stages)
                .build();
    }
}
