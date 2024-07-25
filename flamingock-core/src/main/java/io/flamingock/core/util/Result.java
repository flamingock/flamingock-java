/*
 * Copyright 2023 Flamingock (https://oss.flamingock.io)
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

package io.flamingock.core.util;

public abstract class Result {

    private static Ok okInstance;

    public static Ok OK() {
        if (okInstance == null) {
            okInstance = new Ok();
        }
        return okInstance;
    }


    private Result() {
    }

    public final boolean isOk() {
        return this instanceof Ok;
    }


    public static class Ok extends Result {
        public Ok() {
            super();
        }

    }

    public static class Error extends Result {

        private final Throwable throwable;

        public Error(Throwable throwable) {
            super();
            this.throwable = throwable;
        }


        public Throwable getError() {
            return throwable;
        }
    }
}
