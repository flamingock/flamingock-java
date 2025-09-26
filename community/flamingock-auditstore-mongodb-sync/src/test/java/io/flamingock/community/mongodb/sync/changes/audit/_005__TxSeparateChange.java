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
package io.flamingock.community.mongodb.sync.changes.audit;

import io.flamingock.api.annotations.Change;
import io.flamingock.api.annotations.Apply;
import io.flamingock.api.annotations.TargetSystem;

/**
 * Change that produces TX_SEPARATE_NO_MARKER txStrategy via MongoDBSyncTargetSystem with different MongoClient.
 * Used for testing audit persistence of TX_SEPARATE_NO_MARKER transaction type.
 */
@TargetSystem(id = "tx-separate-system")
@Change(id = "tx-separate-no-marker", transactional = true, author = "aperezdieppa")
public class _005__TxSeparateChange {

    @Apply
    public void execution() {
        // Simple operation that completes successfully
        System.out.println("Executing TX_SEPARATE_NO_MARKER change via MongoDBSyncTargetSystem with different MongoClient");
    }
}