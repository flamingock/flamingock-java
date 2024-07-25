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

package io.flamingock.core.util;

public class ServerException extends RuntimeException {

    private final String request;

    private final String body;

    private final FlamingockError error;

    public ServerException(String request,
                           String body,
                           FlamingockError error) {
        super(error.toString());
        this.request = request;
        this.body = body;
        this.error = error;
    }

    public FlamingockError getError() {
        return error;
    }

    public String getRequestString() {
        return request;
    }

    public String getBodyString() {
        return body;
    }
}
