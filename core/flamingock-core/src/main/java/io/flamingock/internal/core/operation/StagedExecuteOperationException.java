/*
 * Copyright 2023 Flamingock (https://www.flamingock.io)
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
package io.flamingock.internal.core.operation;

import io.flamingock.internal.common.core.response.data.ExecuteResponseData;
import io.flamingock.internal.common.core.response.data.StageResult;

import java.util.stream.Collectors;

/**
 * Thrown when one or more stages ended in a failed state ({@code Failed} or
 * {@code BlockedForMI}) but the pipeline iteration completed. The per-stage error details live
 * in {@code getResult().getStages()}.
 *
 * <p>The exception message is a single-line, log-aggregator-friendly summary built from the
 * carried {@link ExecuteResponseData}: failed stage count + names, change counts, and run
 * duration. Multi-line / per-stage detail rendering is deferred to a future {@code toString()}
 * override and a default event listener (see {@code docs/ERROR_REPORTING_PROPOSAL.md}).
 */
public class StagedExecuteOperationException extends ExecuteOperationException {

    public StagedExecuteOperationException(ExecuteResponseData result) {
        super(buildMessage(result), result);
    }

    private static String buildMessage(ExecuteResponseData result) {
        String failedStageNames = result.getStages().stream()
                .filter(s -> s.getState().isFailed())
                .map(StageResult::getStageName)
                .collect(Collectors.joining(", "));
        return String.format(
                "Flamingock execution failed: %d of %d stage(s) failed [%s]; changes applied=%d, failed=%d, skipped=%d; duration=%dms",
                result.getFailedStages(),
                result.getTotalStages(),
                failedStageNames,
                result.getAppliedChanges(),
                result.getFailedChanges(),
                result.getSkippedChanges(),
                result.getTotalDurationMs());
    }
}
