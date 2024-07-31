/*
 * Copyright 2023 Flamingock ("https://oss.flamingock.io")
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.flamingock.commons.utils;

public class FlamingockError {

    public static final String GENERIC_ERROR = "CLIENT_GENERIC_ERROR";

    public static final String OBJECT_MAPPING_ERROR = "CLIENT_OBJECT_MAPPING_ERROR";

    public static final String HTTP_CONNECTION_ERROR = "CLIENT_HTTP_CONNECTION_ERROR";

    private String code;

    private String message;

    private boolean recoverable;

    public FlamingockError() {
    }

    public FlamingockError(String code, boolean recoverable, String message) {
        this.code = code;
        this.recoverable = recoverable;
        this.message = message;
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

    @Override
    public String toString() {
        return "FlamingockError{" +
                "code='" + code + '\'' +
                ", message='" + message + '\'' +
                ", recoverable=" + recoverable +
                '}';
    }
}
