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
package io.flamingock.community.dynamodb.changes.audit;

import io.flamingock.api.annotations.Apply;
import io.flamingock.api.annotations.Change;
import io.flamingock.api.annotations.TargetSystem;

/**
 * Change that produces NON_TX txStrategy via non-transactional DefaultTargetSystem.
 * Uses custom target system for testing targetSystemId persistence.
 */
@TargetSystem(id = "non-tx-system")
@Change(id = "non-tx-target-system", author = "test-author")
public class _002__NonTxTargetSystemChangeNoDependencies {

    @Apply
    public void apply() {
        // Simple execution - this will be NON_TX due to DefaultTargetSystem
        System.out.println("Executing NON_TX change via DefaultTargetSystem");
    }
}