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
package io.flamingock.targetsystem.couchbase.changes.unhappypath;

import com.couchbase.client.core.io.CollectionIdentifier;
import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.Collection;
import com.couchbase.client.java.json.JsonObject;
import com.couchbase.client.java.transactions.TransactionAttemptContext;
import io.flamingock.api.annotations.Change;
import io.flamingock.api.annotations.Apply;
import io.flamingock.api.annotations.NonLockGuarded;
import io.flamingock.api.annotations.TargetSystem;

@TargetSystem( id = "couchbase-ts")
@Change(id = "insert-clients", order = "002")
public class UnhappyInsertClientsChange {

    @Apply
    public void execution(@NonLockGuarded Bucket bucket, @NonLockGuarded TransactionAttemptContext ctx) {
        Collection collection = bucket
                .scope(CollectionIdentifier.DEFAULT_SCOPE)
                .collection("clientCollection");
        ctx.insert(collection, "test-client-Federico", JsonObject.create().put("name", "Should Have Been Rolled Back"));
        throw new RuntimeException("Intended exception");
    }
}
