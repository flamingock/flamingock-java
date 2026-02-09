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
package io.flamingock.internal.core.operation.result;

import io.flamingock.internal.common.core.response.data.ChangeResult;
import io.flamingock.internal.common.core.response.data.ChangeStatus;
import io.flamingock.internal.common.core.response.data.ErrorInfo;
import io.flamingock.internal.common.core.response.data.ExecuteResponseData;
import io.flamingock.internal.common.core.response.data.ExecutionStatus;
import io.flamingock.internal.common.core.response.data.StageResult;
import io.flamingock.internal.common.core.response.data.StageStatus;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Builder for creating ExecuteResponseData instances from pipeline execution data.
 */
public class ExecutionResultBuilder {

    private ExecutionStatus status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private final List<StageResult> stages = new ArrayList<>();
    private ErrorInfo error;

    public ExecutionResultBuilder() {
    }

    public ExecutionResultBuilder startTimer() {
        this.startTime = LocalDateTime.now();
        return this;
    }

    public ExecutionResultBuilder stopTimer() {
        this.endTime = LocalDateTime.now();
        return this;
    }

    public ExecutionResultBuilder addStage(StageResult stage) {
        this.stages.add(stage);
        return this;
    }

    public ExecutionResultBuilder status(ExecutionStatus status) {
        this.status = status;
        return this;
    }

    public ExecutionResultBuilder success() {
        this.status = ExecutionStatus.SUCCESS;
        return this;
    }

    public ExecutionResultBuilder failed() {
        this.status = ExecutionStatus.FAILED;
        return this;
    }

    public ExecutionResultBuilder partial() {
        this.status = ExecutionStatus.PARTIAL;
        return this;
    }

    public ExecutionResultBuilder noChanges() {
        this.status = ExecutionStatus.NO_CHANGES;
        return this;
    }

    public ExecutionResultBuilder error(ErrorInfo error) {
        this.error = error;
        return this;
    }

    public ExecutionResultBuilder error(Throwable throwable, String changeId, String stageId) {
        this.error = ErrorInfo.fromThrowable(throwable, changeId, stageId);
        return this;
    }

    public ExecuteResponseData build() {
        long durationMs = 0;
        if (startTime != null && endTime != null) {
            durationMs = Duration.between(startTime, endTime).toMillis();
        }

        // Calculate aggregate counts from stages
        int totalStages = stages.size();
        int completedStages = 0;
        int failedStages = 0;
        int totalChanges = 0;
        int appliedChanges = 0;
        int skippedChanges = 0;
        int failedChanges = 0;

        for (StageResult stage : stages) {
            if (stage.getStatus() == StageStatus.COMPLETED) {
                completedStages++;
            } else if (stage.getStatus() == StageStatus.FAILED) {
                failedStages++;
            }

            for (ChangeResult change : stage.getChanges()) {
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

        // Determine status if not explicitly set
        if (status == null) {
            if (failedChanges > 0) {
                status = ExecutionStatus.FAILED;
            } else if (appliedChanges > 0) {
                status = ExecutionStatus.SUCCESS;
            } else {
                status = ExecutionStatus.NO_CHANGES;
            }
        }

        Instant startInstant = startTime != null
                ? startTime.atZone(ZoneId.systemDefault()).toInstant()
                : null;
        Instant endInstant = endTime != null
                ? endTime.atZone(ZoneId.systemDefault()).toInstant()
                : null;

        return ExecuteResponseData.builder()
                .status(status)
                .startTime(startInstant)
                .endTime(endInstant)
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
