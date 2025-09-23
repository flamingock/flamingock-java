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
 * Change unit that produces NON_TX txStrategy via transactional=false annotation.
 * Used for testing audit persistence of NON_TX transaction type.
 */
@TargetSystem(id = "mongodb")
@Change(id = "non-tx-transactional-false", order = "001", transactional = false, author = "aperezdieppa")
public class NonTxTransactionalFalseChange {

    @Apply
    public void execution() {
        // Simple operation that completes successfully
        System.out.println("Executing NON_TX change via transactional=false");
    }
}
