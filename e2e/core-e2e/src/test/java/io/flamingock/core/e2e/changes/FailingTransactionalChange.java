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
import io.flamingock.api.annotations.Rollback;
import io.flamingock.api.annotations.TargetSystem;

/**
 * Failing transactional change unit for testing error handling and rollback scenarios.
 */
@TargetSystem(id = "salesforce")
@Change(id = "test4-failing-tx-change", order = "006", transactional = true, author = "aperezdieppa")
public class FailingTransactionalChange {

    @Apply
    public void execution() {
        System.out.println("Executing failing transactional change");
        throw new RuntimeException("Simulated execution failure");
    }
    
    @Rollback
    public void rollback() {
        System.out.println("Rolling back failing transactional change");
    }
}
