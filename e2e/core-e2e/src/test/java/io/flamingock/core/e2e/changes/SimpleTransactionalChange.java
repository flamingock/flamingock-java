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
package io.flamingock.core.e2e.changes;

import io.flamingock.api.annotations.Change;
import io.flamingock.api.annotations.Apply;

/**
 * Simple transactional change unit for testing core execution strategies.
 * Does not require any external dependencies.
 */
@Change(id = "test2-tx-change", order = "002", transactional = true)
public class SimpleTransactionalChange {

    @Apply
    public void execution() {
        // Simple operation that completes successfully in transaction
        System.out.println("Executing simple transactional change");
    }
}