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
import io.flamingock.internal.util.ThrowableUtil;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

public class OperationException extends FlamingockException {

    private static final int MAX_UNWRAP_DEPTH = 32;

    public static OperationException fromExisting(Throwable exception, ExecuteResponseData result) {
        Throwable root = unwrapKnownWrappers(exception);

        if (root instanceof FlamingockException) {
            Throwable cause = root.getCause();
            if (cause != null) {
                // Keep the real cause, avoid FlamingockException(...) chain
                return new OperationException(cause, result);
            }
            // No cause available: avoid OperationException(FlamingockException)
            return new OperationException(ThrowableUtil.messageOf(root), result);
        }

        return new OperationException(root, result);
    }


    private static Throwable unwrapKnownWrappers(Throwable t) {
        if (t == null) return new NullPointerException("Throwable is null");

        Throwable cur = t;
        int guard = 0;

        while (guard++ < MAX_UNWRAP_DEPTH) {
            if (cur instanceof java.lang.reflect.InvocationTargetException) {
                Throwable next = ((java.lang.reflect.InvocationTargetException) cur).getTargetException();
                if (next != null && next != cur) { cur = next; continue; }
                break;
            }
            if (cur instanceof java.lang.reflect.UndeclaredThrowableException) {
                Throwable next = ((java.lang.reflect.UndeclaredThrowableException) cur).getUndeclaredThrowable();
                if (next != null && next != cur) { cur = next; continue; }
                break;
            }
            if (cur instanceof java.util.concurrent.ExecutionException
                    || cur instanceof java.util.concurrent.CompletionException) {
                Throwable next = cur.getCause();
                if (next != null && next != cur) { cur = next; continue; }
                break;
            }
            break;
        }
        return cur;
    }

    private final ExecuteResponseData result;

    private OperationException(String message, ExecuteResponseData result) {
        super(message);
        this.result = result;
    }

    private OperationException(Throwable throwable, ExecuteResponseData result) {
        super(throwable);
        this.result = result;
    }

    public ExecuteResponseData getResult() {
        return result;
    }
}
