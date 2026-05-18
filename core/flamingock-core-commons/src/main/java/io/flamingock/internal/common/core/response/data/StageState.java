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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.flamingock.internal.common.core.recovery.RecoveryIssue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = StageState.NotStarted.class, name = "NOT_STARTED"),
        @JsonSubTypes.Type(value = StageState.Started.class, name = "STARTED"),
        @JsonSubTypes.Type(value = StageState.Completed.class, name = "COMPLETED"),
        @JsonSubTypes.Type(value = StageState.Failed.class, name = "FAILED"),
        @JsonSubTypes.Type(value = StageState.BlockedForMI.class, name = "BLOCKED_MANUAL_INTERVENTION")
})
public abstract class StageState {

    public static final StageState NOT_STARTED = new NotStarted();
    public static final StageState STARTED = new Started();
    public static final StageState COMPLETED = new Completed();

    public static StageState failed(ErrorInfo info) {
        return new Failed(info);
    }

    public static StageState blockedManualIntervention(String stageName, List<RecoveryIssue> issues) {
        List<String> changeIds = issues.stream()
                .map(RecoveryIssue::getChangeId)
                .collect(Collectors.toList());
        ErrorInfo errorInfo = new ErrorInfo(
                "MANUAL_INTERVENTION_REQUIRED",
                "Manual intervention required",
                changeIds,
                stageName
        );
        return new BlockedForMI(errorInfo, issues);
    }

    StageState() {
    }

    public boolean isNotStarted() {
        return false;
    }

    public boolean isStarted() {
        return false;
    }

    public boolean isCompleted() {
        return false;
    }

    public boolean isFailed() {
        return false;
    }

    public boolean isBlockedForManualIntervention() {
        return false;
    }

    public Optional<ErrorInfo> getErrorInfo() {
        return Optional.empty();
    }

    public List<RecoveryIssue> getRecoveryIssues() {
        return Collections.emptyList();
    }

    static final class NotStarted extends StageState {
        @Override
        public boolean isNotStarted() {
            return true;
        }
    }

    static final class Started extends StageState {
        @Override
        public boolean isStarted() {
            return true;
        }
    }

    static final class Completed extends StageState {
        @Override
        public boolean isCompleted() {
            return true;
        }
    }

    static class Failed extends StageState {
        private final ErrorInfo errorInfo;

        @JsonCreator
        Failed(@JsonProperty("errorInfo") ErrorInfo errorInfo) {
            this.errorInfo = errorInfo;
        }

        @Override
        public boolean isFailed() {
            return true;
        }

        @Override
        public Optional<ErrorInfo> getErrorInfo() {
            return Optional.ofNullable(errorInfo);
        }

        @JsonProperty("errorInfo")
        ErrorInfo serialisedErrorInfo() {
            return errorInfo;
        }
    }

    static final class BlockedForMI extends Failed {
        private final List<RecoveryIssue> recoveryIssues;

        @JsonCreator
        BlockedForMI(
                @JsonProperty("errorInfo") ErrorInfo errorInfo,
                @JsonProperty("recoveryIssues") List<RecoveryIssue> recoveryIssues) {
            super(errorInfo);
            this.recoveryIssues = Collections.unmodifiableList(new ArrayList<>(recoveryIssues));
        }

        @Override
        public boolean isBlockedForManualIntervention() {
            return true;
        }

        @Override
        @JsonProperty("recoveryIssues")
        public List<RecoveryIssue> getRecoveryIssues() {
            return recoveryIssues;
        }
    }
}
