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

package io.flamingock.api;

public enum StageType {
    DEFAULT,
    LEGACY("legacy"),
    SYSTEM("importer");

    private final String alias;

    StageType(String alias) {
        this.alias = alias;
    }

    StageType() {
        this.alias = null;
    }

    public static StageType from(String name) {
        if (name == null || name.isEmpty()) {
            return DEFAULT;
        }
        for (StageType stageType : StageType.values()) {
            if (name.equals(stageType.alias)) {
                return stageType;
            }
        }
        throw new IllegalArgumentException("No such stage type: " + name);
    }
}