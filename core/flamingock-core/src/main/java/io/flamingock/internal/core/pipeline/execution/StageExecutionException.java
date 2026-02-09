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
package io.flamingock.internal.core.pipeline.execution;

import io.flamingock.internal.common.core.error.FlamingockException;
import io.flamingock.internal.common.core.response.data.StageResult;

/**
 * Exception thrown when a stage execution fails.
 * Contains the stage result data with information about what was executed.
 */
public class StageExecutionException extends FlamingockException {

    public static StageExecutionException fromResult(Throwable exception, StageResult result, String failedChangeId) {
        Throwable cause = exception.getCause();
        return (exception instanceof FlamingockException) && cause != null
                ? new StageExecutionException(cause, result, failedChangeId)
                : new StageExecutionException(exception, result, failedChangeId);
    }

    private final StageResult result;
    private final String failedChangeId;

    private StageExecutionException(Throwable cause, StageResult result, String failedChangeId) {
        super(cause);
        this.result = result;
        this.failedChangeId = failedChangeId;
    }

    public StageResult getResult() {
        return result;
    }

    public String getFailedChangeId() {
        return failedChangeId;
    }
}
