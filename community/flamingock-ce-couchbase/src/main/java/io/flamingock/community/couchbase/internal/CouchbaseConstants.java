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

package io.flamingock.community.couchbase.internal;

public final class CouchbaseConstants {
    public static final String INDEX_NAME = "idx_mongock_keys";
    public static final String DOCUMENT_TYPE_KEY = "_doctype";
    public static final String DOCUMENT_TYPE_AUDIT_ENTRY = "mongockChangeEntry";
    public static final  String DOCUMENT_TYPE_LOCK_ENTRY = "mongockLockEntry";
}
