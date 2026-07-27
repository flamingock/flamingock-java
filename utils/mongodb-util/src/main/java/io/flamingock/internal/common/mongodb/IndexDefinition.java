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
package io.flamingock.internal.common.mongodb;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Persistence-agnostic description of a desired collection index.
 * <p>
 * Supports the three variants the audit stores need: a plain unique index (the legacy shape used
 * by the audit and lock collections), a non-unique index, and a partial index (an index that only
 * covers documents matching a {@code partialFilterExpression}). The concrete {@link CollectionHelper}
 * translates this definition into a driver-specific index at creation time.
 */
public final class IndexDefinition {

    private final LinkedHashMap<String, Integer> keys;
    private final boolean unique;
    private final Map<String, Object> partialFilterExpression;
    private final String name;

    public IndexDefinition(LinkedHashMap<String, Integer> keys,
                           boolean unique,
                           Map<String, Object> partialFilterExpression,
                           String name) {
        if (keys == null || keys.isEmpty()) {
            throw new IllegalArgumentException("index keys must not be empty");
        }
        this.keys = new LinkedHashMap<>(keys);
        this.unique = unique;
        this.partialFilterExpression = partialFilterExpression == null
                ? null
                : Collections.unmodifiableMap(new LinkedHashMap<>(partialFilterExpression));
        this.name = name;
    }

    /**
     * Builds a plain ascending unique index on the given fields. This reproduces the exact shape
     * historically created for the audit and lock collections (all fields ascending, {@code unique=true},
     * no partial filter, server-generated name).
     */
    public static IndexDefinition uniqueOn(String... fields) {
        LinkedHashMap<String, Integer> keys = new LinkedHashMap<>();
        for (String field : fields) {
            keys.put(field, 1);
        }
        return new IndexDefinition(keys, true, null, null);
    }

    public LinkedHashMap<String, Integer> getKeys() {
        return keys;
    }

    public boolean isUnique() {
        return unique;
    }

    public Map<String, Object> getPartialFilterExpression() {
        return partialFilterExpression;
    }

    public boolean hasPartialFilter() {
        return partialFilterExpression != null && !partialFilterExpression.isEmpty();
    }

    public String getName() {
        return name;
    }
}
