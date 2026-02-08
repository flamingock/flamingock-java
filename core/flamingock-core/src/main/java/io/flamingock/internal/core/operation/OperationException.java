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

import io.flamingock.internal.common.core.error.FlamingockException;
import io.flamingock.internal.common.core.response.data.ExecuteResponseData;

/**
 * Exception thrown when a pipeline operation fails.
 * Contains the execution result data with information about what was executed.
 */
public class OperationException extends FlamingockException {

    public static OperationException fromExisting(Throwable exception, ExecuteResponseData result) {
        Throwable cause = exception.getCause();
        return (exception instanceof FlamingockException) && cause != null
                ? new OperationException(cause, result)
                : new OperationException(exception, result);
    }

    private final ExecuteResponseData result;

    private OperationException(Throwable throwable, ExecuteResponseData result) {
        super(throwable);
        this.result = result;
    }

    public ExecuteResponseData getResult() {
        return result;
    }
}
