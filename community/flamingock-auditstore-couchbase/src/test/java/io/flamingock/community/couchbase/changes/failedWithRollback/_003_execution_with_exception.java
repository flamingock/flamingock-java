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
package io.flamingock.community.couchbase.changes.failedWithRollback;

import com.couchbase.client.java.Collection;
import com.couchbase.client.java.json.JsonObject;
import io.flamingock.api.annotations.Change;
import io.flamingock.api.annotations.Apply;
import io.flamingock.api.annotations.TargetSystem;
import io.flamingock.api.annotations.Rollback;

@TargetSystem(id = "couchbase")
@Change( id="execution-with-exception" , order = "003", transactional = false, author = "aperezdieppa")
public class _003_execution_with_exception {

    @Apply
    public void execution(Collection collection) {
        collection.insert("test-client-Jorge", JsonObject.create().put("name", "Jorge"));
        throw new RuntimeException("test");
    }

    @Rollback
    public void rollbackExecution(Collection collection) {
        collection.remove("test-client-Jorge");
    }
}
