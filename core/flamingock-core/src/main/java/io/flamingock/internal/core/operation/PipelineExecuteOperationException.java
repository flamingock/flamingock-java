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
 * Thrown when a pipeline-wide error breaks iteration (e.g. {@code LockException}, planner abort,
 * unexpected throwable). Always carries the response data assembled so far plus the originating
 * cause.
 */
public class PipelineExecuteOperationException extends ExecuteOperationException {

    public PipelineExecuteOperationException(Throwable cause, ExecuteResponseData result) {
        super(cause, result);
    }

    public PipelineExecuteOperationException(String message, Throwable cause, ExecuteResponseData result) {
        super(message, cause, result);
    }

    // Rich multi-line report; getMessage() stays as Throwable's default (cause-derived) for log aggregators.
    @Override
    public String toString() {
        return ExecutionReportFormatter.report(getResult());
    }
}
