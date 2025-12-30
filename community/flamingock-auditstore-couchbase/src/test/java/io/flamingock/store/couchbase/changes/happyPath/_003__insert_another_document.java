/*
 * Copyright 2023 Flamingock (https://www.flamingock.io)
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
package io.flamingock.store.couchbase.changes.happyPath;

import com.couchbase.client.java.Collection;
import com.couchbase.client.java.json.JsonObject;
import com.couchbase.client.java.transactions.TransactionAttemptContext;
import io.flamingock.api.annotations.Change;
import io.flamingock.api.annotations.Apply;
import io.flamingock.api.annotations.TargetSystem;
import io.flamingock.api.annotations.NonLockGuarded;

@TargetSystem(id = "couchbase")
@Change( id="insert-another-document", author = "aperezdieppa")
public class _003__insert_another_document {

    @Apply
    public void apply(Collection collection, @NonLockGuarded TransactionAttemptContext ctx) {
        ctx.insert(collection,"test-client-Jorge", JsonObject.create().put("name", "Jorge"));
    }
}
