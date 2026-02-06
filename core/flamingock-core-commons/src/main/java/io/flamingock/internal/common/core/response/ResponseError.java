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
package io.flamingock.internal.common.core.response;

/**
 * Represents an error that occurred during operation execution.
 */
public class ResponseError {

    private String code;
    private String message;
    private boolean recoverable;

    public ResponseError() {
    }

    public ResponseError(String code, String message, boolean recoverable) {
        this.code = code;
        this.message = message;
        this.recoverable = recoverable;
    }

    public static ResponseError from(Throwable throwable) {
        String code = deriveErrorCode(throwable);
        String message = throwable.getMessage() != null ? throwable.getMessage() : throwable.getClass().getSimpleName();
        boolean recoverable = isRecoverable(throwable);
        return new ResponseError(code, message, recoverable);
    }

    private static String deriveErrorCode(Throwable throwable) {
        String className = throwable.getClass().getSimpleName();
        if (className.contains("Lock")) {
            return "LOCK_ERROR";
        } else if (className.contains("Audit") || className.contains("Store")) {
            return "AUDIT_STORE_ERROR";
        } else if (className.contains("Connection") || className.contains("Database")) {
            return "CONNECTION_ERROR";
        } else if (className.contains("Bootstrap") || className.contains("Config")) {
            return "BOOTSTRAP_FAILURE";
        }
        return "EXECUTION_ERROR";
    }

    private static boolean isRecoverable(Throwable throwable) {
        String className = throwable.getClass().getSimpleName();
        return className.contains("Lock") || className.contains("Connection") || className.contains("Timeout");
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public boolean isRecoverable() {
        return recoverable;
    }

    public void setRecoverable(boolean recoverable) {
        this.recoverable = recoverable;
    }
}
