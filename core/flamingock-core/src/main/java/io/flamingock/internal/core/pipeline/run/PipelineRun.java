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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class PipelineRun {

    public static PipelineRun of(LoadedPipeline pipeline) {
        List<AbstractLoadedStage> stages = new ArrayList<>();
        pipeline.getSystemStage().ifPresent(stages::add);
        stages.addAll(pipeline.getStages());
        return of(stages);
    }

    public static PipelineRun of(List<AbstractLoadedStage> stages) {
        List<StageRun> runs = new ArrayList<>();
        for (AbstractLoadedStage stage : stages) {
            runs.add(new StageRun(stage));
        }
        return new PipelineRun(runs);
    }

    private final List<StageRun> stageRuns;
    private final Map<String, StageRun> byName;
    private Instant startedAt;
    private Instant stoppedAt;
    private ErrorInfo pipelineError;

    private PipelineRun(List<StageRun> stageRuns) {
        this.stageRuns = Collections.unmodifiableList(stageRuns);
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
        // Incoming result from the executor already carries state=COMPLETED.
        lookupOrThrow(stageName).setResult(result);
    }

    public void markStageFailed(String stageName, StageExecutionException exception) {
        StageResult resultFromExecutor = exception.getResult();
        ErrorInfo errorInfo = ErrorInfo.fromThrowable(
                exception.getCause(),
                Collections.singletonList(exception.getFailedChangeId()),
                resultFromExecutor.getStageId()
        );
        StageRun run = lookupOrThrow(stageName);
        run.setResult(StageResult.builder(resultFromExecutor)
                .state(StageState.failed(errorInfo))
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

    /**
     * Pipeline-wide failure marker. Idempotent: first call wins.
     */
    public void markPipelineFailed(Throwable cause) {
        if (this.pipelineError == null) {
            this.pipelineError = ErrorInfo.fromThrowable(cause, Collections.emptyList(), null);
        }
    }

    public Optional<ErrorInfo> getPipelineError() {
        return Optional.ofNullable(pipelineError);
    }

    private StageRun lookupOrThrow(String stageName) {
        StageRun run = byName.get(stageName);
        if (run == null) {
            throw new IllegalArgumentException("Unknown stage: " + stageName);
        }
        return run;
    }

    public ExecuteResponseData toResponse() {
        List<StageResult> stages = new ArrayList<>();
        int totalStages = 0;
        int completedStages = 0;
        int failedStages = 0;
        int totalChanges = 0;
        int appliedChanges = 0;
        int skippedChanges = 0;
        int failedChanges = 0;
        ErrorInfo error = null;
        boolean anyFailed = false;

        for (StageRun stageRun : stageRuns) {
            StageResult stageResult = stageRun.getResult();
            stages.add(stageResult);
            totalStages++;

            StageState state = stageResult.getState();
            if (state.isCompleted()) {
                completedStages++;
            } else if (state.isFailed()) {
                failedStages++;
                anyFailed = true;
                if (error == null) {
                    error = state.getErrorInfo().orElse(null);
                }
            }

            if (stageResult.getChanges() != null) {
                for (ChangeResult change : stageResult.getChanges()) {
                    totalChanges++;
                    if (change.getStatus() == ChangeStatus.APPLIED) {
                        appliedChanges++;
                    } else if (change.getStatus() == ChangeStatus.ALREADY_APPLIED) {
                        skippedChanges++;
                    } else if (change.getStatus() == ChangeStatus.FAILED) {
                        failedChanges++;
                    }
                }
            }
        }

        if (error == null) {
            error = this.pipelineError;
        }
        boolean pipelineFailed = anyFailed || this.pipelineError != null;
        ExecutionStatus status = pipelineFailed ? ExecutionStatus.FAILED : ExecutionStatus.SUCCESS;
        long durationMs = (startedAt != null && stoppedAt != null)
                ? Duration.between(startedAt, stoppedAt).toMillis()
                : 0L;

        return ExecuteResponseData.builder()
                .status(status)
                .startTime(startedAt)
                .endTime(stoppedAt)
                .totalDurationMs(durationMs)
                .totalStages(totalStages)
                .completedStages(completedStages)
                .failedStages(failedStages)
                .totalChanges(totalChanges)
                .appliedChanges(appliedChanges)
                .skippedChanges(skippedChanges)
                .failedChanges(failedChanges)
                .stages(stages)
                .error(error)
                .build();
    }
}
