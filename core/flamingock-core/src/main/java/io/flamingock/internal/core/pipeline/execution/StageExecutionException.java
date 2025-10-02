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

public class StageExecutionException extends FlamingockException {

    public static StageExecutionException fromExisting(Throwable exception, StageSummary summary) {
        Throwable cause = exception.getCause();
        return (exception instanceof FlamingockException) && cause != null
                ? new StageExecutionException(cause, summary)
                : new StageExecutionException(exception, summary);
    }

    private final StageSummary summary;

    private StageExecutionException(Throwable cause, StageSummary summary) {
        super(cause);
        this.summary = summary;
    }

    public StageSummary getSummary() {
        return summary;
    }


}
