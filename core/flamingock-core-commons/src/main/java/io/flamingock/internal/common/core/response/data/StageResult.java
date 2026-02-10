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

import java.util.ArrayList;
import java.util.List;

/**
 * Result data for a stage execution.
 */
public class StageResult {

    private String stageId;
    private String stageName;
    private StageStatus status;
    private long durationMs;
    private List<ChangeResult> changes;

    public StageResult() {
        this.changes = new ArrayList<>();
    }

    private StageResult(Builder builder) {
        this.stageId = builder.stageId;
        this.stageName = builder.stageName;
        this.status = builder.status;
        this.durationMs = builder.durationMs;
        this.changes = builder.changes != null ? builder.changes : new ArrayList<>();
    }

    public String getStageId() {
        return stageId;
    }

    public void setStageId(String stageId) {
        this.stageId = stageId;
    }

    public String getStageName() {
        return stageName;
    }

    public void setStageName(String stageName) {
        this.stageName = stageName;
    }

    public StageStatus getStatus() {
        return status;
    }

    public void setStatus(StageStatus status) {
        this.status = status;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }

    public List<ChangeResult> getChanges() {
        return changes;
    }

    public void setChanges(List<ChangeResult> changes) {
        this.changes = changes;
    }

    public boolean isFailed() {
        return status == StageStatus.FAILED;
    }

    public boolean isCompleted() {
        return status == StageStatus.COMPLETED;
    }

    public int getAppliedCount() {
        return (int) changes.stream()
                .filter(ChangeResult::isApplied)
                .count();
    }

    public int getSkippedCount() {
        return (int) changes.stream()
                .filter(ChangeResult::isAlreadyApplied)
                .count();
    }

    public int getFailedCount() {
        return (int) changes.stream()
                .filter(ChangeResult::isFailed)
                .count();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String stageId;
        private String stageName;
        private StageStatus status;
        private long durationMs;
        private List<ChangeResult> changes = new ArrayList<>();

        public Builder stageId(String stageId) {
            this.stageId = stageId;
            return this;
        }

        public Builder stageName(String stageName) {
            this.stageName = stageName;
            return this;
        }

        public Builder status(StageStatus status) {
            this.status = status;
            return this;
        }

        public Builder durationMs(long durationMs) {
            this.durationMs = durationMs;
            return this;
        }

        public Builder changes(List<ChangeResult> changes) {
            this.changes = changes;
            return this;
        }

        public Builder addChange(ChangeResult change) {
            if (this.changes == null) {
                this.changes = new ArrayList<>();
            }
            this.changes.add(change);
            return this;
        }

        public StageResult build() {
            return new StageResult(this);
        }
    }
}
