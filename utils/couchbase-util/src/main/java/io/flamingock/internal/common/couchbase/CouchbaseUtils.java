/*
 * Copyright 2025 Flamingock (https://www.flamingock.io)
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
package io.flamingock.internal.common.couchbase;

import com.couchbase.client.core.io.CollectionIdentifier;
import com.couchbase.client.java.json.JsonObject;
import io.flamingock.internal.common.core.error.FlamingockException;

import java.util.Date;
import java.util.Optional;

public final class CouchbaseUtils {
    private CouchbaseUtils() {
    }

    public static boolean isDefaultScope(String scopeName) {
        return CollectionIdentifier.DEFAULT_SCOPE.equals(scopeName);
    }

    public static boolean isDefaultCollection(String scopeName, String collectionName) {
        return CollectionIdentifier.DEFAULT_SCOPE.equals(scopeName)
                && CollectionIdentifier.DEFAULT_COLLECTION.equals(collectionName);
    }

    /**
     * By default, couchbase does not support custom object serialization, like Date
     * or Optional.
     * Still we can register custom Jackson modules to support that, but that will
     * also change the overall
     * couchbase client functionality. So to keep it simple just try to convert
     * values in place.
     *
     * @param document The document to which to add a given key/value pair.
     * @param key      The key of the object in document.
     * @param value    The value of the object in document.
     */
    public static void addFieldToDocument(JsonObject document, String key, Object value) {
        if (value instanceof Date) {
            document.put(key, ((Date) value).getTime());
        } else if (value instanceof Optional) {
            Optional<?> optional = (Optional<?>) value;
            if (optional.isPresent()) {
                addFieldToDocument(document, key, optional.get());
            }
        } else {
            try {
                document.put(key, value);
            } catch (IllegalArgumentException e) {
                throw new RuntimeException("Unsupported Couchbase type " + value.getClass().getName());
            }
        }
    }

    public static ScopeCollection getOriginScopeAndCollection(String origin) {

        // Default value
        if (origin == null || origin.trim().isEmpty()) {
            return new ScopeCollection(CollectionIdentifier.DEFAULT_SCOPE, CollectionIdentifier.DEFAULT_COLLECTION);
        }

        String value = origin.trim();

        // Separator validation (only one '.')
        if (hasMoreThanOneDot(value)) {
            throw new FlamingockException(
                    "Invalid origin '" + origin + "'. Only one '.' separator is allowed."
            );
        }

        String[] parts = value.split("\\.", 2);

        // Only collection
        if (parts.length == 1) {
            String collection = parts[0].trim();

            if (collection.isEmpty()) {
                throw new FlamingockException(
                        "Invalid origin '" + origin + "'. Collection name cannot be empty."
                );
            }

            return new ScopeCollection(CollectionIdentifier.DEFAULT_SCOPE, collection);
        }

        // Scope + collection
        String scope = parts[0].trim();
        String collection = parts[1].trim();

        if (scope.isEmpty() || collection.isEmpty()) {
            throw new IllegalArgumentException(
                    "Invalid origin '" + origin + "'. Scope and collection must be non-empty."
            );
        }

        return new ScopeCollection(scope, collection);
    }

    private static boolean hasMoreThanOneDot(String value) {
        int first = value.indexOf('.');
        return first != -1 && value.indexOf('.', first + 1) != -1;
    }

    public static final class ScopeCollection {

        private final String scope;
        private final String collection;

        private ScopeCollection(String scope, String collection) {
            this.scope = scope;
            this.collection = collection;
        }

        public String getScope() {
            return scope;
        }

        public String getCollection() {
            return collection;
        }
    }
}
