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
import io.flamingock.internal.common.core.response.data.ErrorInfo;
import io.flamingock.internal.common.core.response.data.StageResult;
import io.flamingock.internal.common.core.response.data.StageState;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Builder for creating StageResult instances from stage execution data.
 */
public class StageResultBuilder {

    private String stageId;
    private String stageName;
    private StageState state;
    private long durationMs;
    private List<ChangeResult> changes = new ArrayList<>();
    private LocalDateTime startTime;

    public StageResultBuilder() {
    }

    public StageResultBuilder stageId(String stageId) {
        this.stageId = stageId;
        return this;
    }

    public StageResultBuilder stageName(String stageName) {
        this.stageName = stageName;
        return this;
    }

    public StageResultBuilder startTimer() {
        this.startTime = LocalDateTime.now();
        return this;
    }

    public StageResultBuilder stopTimer() {
        if (startTime != null) {
            this.durationMs = Duration.between(startTime, LocalDateTime.now()).toMillis();
        }
        return this;
    }

    public StageResultBuilder state(StageState state) {
        this.state = state;
        return this;
    }

    public StageResultBuilder completed() {
        this.state = StageState.COMPLETED;
        return this;
    }

    public StageResultBuilder failed() {
        this.state = StageState.failed(null);
        return this;
    }

    public StageResultBuilder failed(ErrorInfo errorInfo) {
        this.state = StageState.failed(errorInfo);
        return this;
    }

    public StageResultBuilder notStarted() {
        this.state = StageState.NOT_STARTED;
        return this;
    }

    public StageResultBuilder addChange(ChangeResult change) {
        this.changes.add(change);
        return this;
    }

    public StageResultBuilder changes(List<ChangeResult> changes) {
        this.changes = changes;
        return this;
    }

    public StageResultBuilder durationMs(long durationMs) {
        this.durationMs = durationMs;
        return this;
    }

    public boolean hasFailedChanges() {
        return changes.stream().anyMatch(ChangeResult::isFailed);
    }

    public StageResult build() {
        return StageResult.builder()
                .stageId(stageId)
                .stageName(stageName)
                .state(state)
                .durationMs(durationMs)
                .changes(changes)
                .build();
    }
}
