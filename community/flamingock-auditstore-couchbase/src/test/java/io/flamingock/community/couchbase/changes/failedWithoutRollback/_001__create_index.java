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
package io.flamingock.community.couchbase.changes.failedWithoutRollback;

import com.couchbase.client.java.Collection;
import io.flamingock.api.annotations.Change;
import io.flamingock.api.annotations.Apply;
import io.flamingock.api.annotations.TargetSystem;
import java.util.Arrays;

@TargetSystem(id = "couchbase")
@Change(id = "create-index", transactional = false, author = "aperezdieppa")
public class _001__create_index {

	@Apply
	public void apply(Collection collection) {
		collection.queryIndexes().createIndex( "idx_standalone_index", Arrays.asList("field1", "field2"));
	}
}
