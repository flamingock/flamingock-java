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
package io.flamingock.importer.couchbase.couchbase;

import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.Collection;
import com.couchbase.client.java.json.JsonObject;
import io.flamingock.api.annotations.Change;
import io.flamingock.api.annotations.Apply;

import java.util.Collections;

@Change(id = "create-users-collection", author = "importer", transactional = false)
public class _0001__CreateUsersCollectionChange {

    @Apply
    public void apply(Bucket bucket) {
        Collection collection = bucket.defaultCollection();

        JsonObject user = JsonObject.create()
                .put("email", "admin@company.com")
                .put("name", "Admin")
                .put("roles", Collections.singletonList("superuser"));

        collection.upsert("user::admin@company.com", user);
    }
}
