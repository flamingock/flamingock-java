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

package io.flamingock.internal.core.engine.lock;


import io.flamingock.internal.util.id.Id;

public class LockKey extends Id {

    public static LockKey fromString(String value) {
        return new LockKey(value);
    }

    private LockKey(String key) {
        super(key);
    }


    @Override
    public boolean equals(Object o) {
        return super.equals(o) && o instanceof LockKey;
    }
}
