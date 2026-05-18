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

/**
 * Parent of every operation-thrown exception. Concrete operations throw their own subclass
 * (e.g. {@link ExecuteOperationException} for execute). Higher layers can catch
 * {@code OperationException} generically when they don't need to differentiate.
 */
public abstract class OperationException extends FlamingockException {

    protected OperationException(String message) {
        super(message);
    }

    protected OperationException(Throwable cause) {
        super(cause);
    }

    protected OperationException(String message, Throwable cause) {
        super(message, cause);
    }
}
