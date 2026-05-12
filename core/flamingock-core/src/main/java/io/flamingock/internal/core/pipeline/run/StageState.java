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
package io.flamingock.internal.core.pipeline.run;

import io.flamingock.internal.common.core.response.data.ErrorInfo;

import java.util.Optional;

public abstract class StageState {

    public static final StageState NOT_STARTED = new NotStarted();
    public static final StageState STARTED = new Started();
    public static final StageState COMPLETED = new Completed();

    public static StageState failed(ErrorInfo info) {
        return new Failed(info);
    }

    private StageState() {
    }

    public boolean isFailed() {
        return false;
    }

    public Optional<ErrorInfo> getErrorInfo() {
        return Optional.empty();
    }

    private static final class NotStarted extends StageState {
    }

    private static final class Started extends StageState {
    }

    private static final class Completed extends StageState {
    }

    private static final class Failed extends StageState {
        private final ErrorInfo info;

        Failed(ErrorInfo info) {
            this.info = info;
        }

        @Override
        public boolean isFailed() {
            return true;
        }

        @Override
        public Optional<ErrorInfo> getErrorInfo() {
            return Optional.of(info);
        }
    }
}
