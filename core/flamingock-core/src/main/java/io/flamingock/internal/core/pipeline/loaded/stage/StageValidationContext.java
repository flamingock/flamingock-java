/*
 * Copyright 2025 Flamingock (https://oss.flamingock.io)
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
 
package io.flamingock.internal.core.pipeline.loaded.stage;

public class StageValidationContext {

    public enum SortType {
        UNSORTED, SEQUENTIAL_SIMPLE, SEQUENTIAL_FORMATTED;

        public boolean isSorted() {
            return this != UNSORTED;
        }
    }

    private final SortType sortType;

    public static Builder builder() {
        return new Builder();
    }

    private StageValidationContext(SortType sortType) {
        this.sortType = sortType;
    }

    public SortType getSortType() {
        return sortType;
    }

    public static class Builder {
        private SortType sorted = SortType.SEQUENTIAL_FORMATTED;

        public Builder setSorted(SortType sorted) {
            this.sorted = sorted;
            return this;
        }

        public StageValidationContext build() {
            return new StageValidationContext(sorted);
        }
    }
}
