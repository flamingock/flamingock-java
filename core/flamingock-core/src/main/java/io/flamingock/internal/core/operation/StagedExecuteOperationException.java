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
import io.flamingock.internal.common.core.response.data.ExecutionReportFormatter;

/**
 * Thrown when one or more stages ended in a failed state ({@code Failed} or
 * {@code BlockedForMI}) but the pipeline iteration completed. The per-stage error details live
 * in {@code getResult().getStages()}.
 *
 * <p>The {@link #getMessage()} is a single-line, log-aggregator-friendly summary (failed stage
 * count + names, change counts, run duration, and the IDs of any change requiring manual
 * intervention). The full multi-line per-stage report is available via {@link #toString()} —
 * see {@code docs/ERROR_REPORTING_PROPOSAL.md} for why the two intentionally differ.
 */
public class StagedExecuteOperationException extends ExecuteOperationException {

    public StagedExecuteOperationException(ExecuteResponseData result) {
        super(ExecutionReportFormatter.summary(result), result);
    }

    // Rich multi-line report; getMessage() stays one-line for log aggregators.
    @Override
    public String toString() {
        return ExecutionReportFormatter.report(getResult());
    }
}
