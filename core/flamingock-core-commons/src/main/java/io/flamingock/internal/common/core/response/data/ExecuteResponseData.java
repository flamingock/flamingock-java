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
package io.flamingock.internal.common.core.response.data;

import com.fasterxml.jackson.annotation.JsonTypeName;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Response data for the EXECUTE operation containing full execution results.
 */
@JsonTypeName("execute")
public class ExecuteResponseData {

    private ExecutionStatus status;
    private Instant startTime;
    private Instant endTime;
    private long totalDurationMs;

    // Aggregate counts
    private int totalStages;
    private int completedStages;
    private int failedStages;
    private int totalChanges;
    private int appliedChanges;
    private int skippedChanges;
    private int failedChanges;

    // Per-stage breakdown
    private List<StageResult> stages;

    // Error information (if failed)
    private ErrorInfo error;

    public ExecuteResponseData() {
        this.stages = new ArrayList<>();
    }

    private ExecuteResponseData(Builder builder) {
        this.status = builder.status;
        this.startTime = builder.startTime;
        this.endTime = builder.endTime;
        this.totalDurationMs = builder.totalDurationMs;
        this.totalStages = builder.totalStages;
        this.completedStages = builder.completedStages;
        this.failedStages = builder.failedStages;
        this.totalChanges = builder.totalChanges;
        this.appliedChanges = builder.appliedChanges;
        this.skippedChanges = builder.skippedChanges;
        this.failedChanges = builder.failedChanges;
        this.stages = builder.stages != null ? builder.stages : new ArrayList<>();
        this.error = builder.error;
    }

    public ExecutionStatus getStatus() {
        return status;
    }

    public void setStatus(ExecutionStatus status) {
        this.status = status;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public void setStartTime(Instant startTime) {
        this.startTime = startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public void setEndTime(Instant endTime) {
        this.endTime = endTime;
    }

    public long getTotalDurationMs() {
        return totalDurationMs;
    }

    public void setTotalDurationMs(long totalDurationMs) {
        this.totalDurationMs = totalDurationMs;
    }

    public int getTotalStages() {
        return totalStages;
    }

    public void setTotalStages(int totalStages) {
        this.totalStages = totalStages;
    }

    public int getCompletedStages() {
        return completedStages;
    }

    public void setCompletedStages(int completedStages) {
        this.completedStages = completedStages;
    }

    public int getFailedStages() {
        return failedStages;
    }

    public void setFailedStages(int failedStages) {
        this.failedStages = failedStages;
    }

    public int getTotalChanges() {
        return totalChanges;
    }

    public void setTotalChanges(int totalChanges) {
        this.totalChanges = totalChanges;
    }

    public int getAppliedChanges() {
        return appliedChanges;
    }

    public void setAppliedChanges(int appliedChanges) {
        this.appliedChanges = appliedChanges;
    }

    public int getSkippedChanges() {
        return skippedChanges;
    }

    public void setSkippedChanges(int skippedChanges) {
        this.skippedChanges = skippedChanges;
    }

    public int getFailedChanges() {
        return failedChanges;
    }

    public void setFailedChanges(int failedChanges) {
        this.failedChanges = failedChanges;
    }

    public List<StageResult> getStages() {
        return stages;
    }

    public void setStages(List<StageResult> stages) {
        this.stages = stages;
    }

    public ErrorInfo getError() {
        return error;
    }

    public void setError(ErrorInfo error) {
        this.error = error;
    }

    public boolean isSuccess() {
        return status == ExecutionStatus.SUCCESS || status == ExecutionStatus.NO_CHANGES;
    }

    public boolean isFailed() {
        return status == ExecutionStatus.FAILED;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private ExecutionStatus status;
        private Instant startTime;
        private Instant endTime;
        private long totalDurationMs;
        private int totalStages;
        private int completedStages;
        private int failedStages;
        private int totalChanges;
        private int appliedChanges;
        private int skippedChanges;
        private int failedChanges;
        private List<StageResult> stages = new ArrayList<>();
        private ErrorInfo error;

        public Builder status(ExecutionStatus status) {
            this.status = status;
            return this;
        }

        public Builder startTime(Instant startTime) {
            this.startTime = startTime;
            return this;
        }

        public Builder endTime(Instant endTime) {
            this.endTime = endTime;
            return this;
        }

        public Builder totalDurationMs(long totalDurationMs) {
            this.totalDurationMs = totalDurationMs;
            return this;
        }

        public Builder totalStages(int totalStages) {
            this.totalStages = totalStages;
            return this;
        }

        public Builder completedStages(int completedStages) {
            this.completedStages = completedStages;
            return this;
        }

        public Builder failedStages(int failedStages) {
            this.failedStages = failedStages;
            return this;
        }

        public Builder totalChanges(int totalChanges) {
            this.totalChanges = totalChanges;
            return this;
        }

        public Builder appliedChanges(int appliedChanges) {
            this.appliedChanges = appliedChanges;
            return this;
        }

        public Builder skippedChanges(int skippedChanges) {
            this.skippedChanges = skippedChanges;
            return this;
        }

        public Builder failedChanges(int failedChanges) {
            this.failedChanges = failedChanges;
            return this;
        }

        public Builder stages(List<StageResult> stages) {
            this.stages = stages;
            return this;
        }

        public Builder addStage(StageResult stage) {
            if (this.stages == null) {
                this.stages = new ArrayList<>();
            }
            this.stages.add(stage);
            return this;
        }

        public Builder error(ErrorInfo error) {
            this.error = error;
            return this;
        }

        public ExecuteResponseData build() {
            return new ExecuteResponseData(this);
        }
    }
}
