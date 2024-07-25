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

package io.flamingock.core.util.http;

import io.flamingock.core.util.FlamingockError;

public class HttpFlamingockError extends FlamingockError {

    private final int statusCode;

    public HttpFlamingockError(int statusCode, int errorCode, String message) {
        super(errorCode, message);
        this.statusCode = statusCode;
    }

    public HttpFlamingockError(int statusCode, FlamingockError error) {
        super(error.getErrorCode(), error.getMessage());
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }

    @Override
    public String toString() {
        return "Error{httpStatus[" + statusCode+ "], errorCode[" + getErrorCode() + "], message['" + getMessage() + "']";
    }
}
