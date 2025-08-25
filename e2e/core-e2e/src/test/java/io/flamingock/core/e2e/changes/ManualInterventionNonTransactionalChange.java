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

import io.flamingock.api.annotations.ChangeUnit;
import io.flamingock.api.annotations.Execution;
import io.flamingock.api.annotations.Recovery;

/**
 * Non-transactional change unit configured with explicit MANUAL_INTERVENTION recovery strategy.
 * Used for testing recovery behavior when changes are in inconsistent states
 * and should require explicit manual intervention (same as default behavior).
 */
@ChangeUnit(id = "manual-intervention-non-tx-change", order = "001", transactional = false)
@Recovery(strategy = Recovery.RecoveryStrategy.MANUAL_INTERVENTION)
public class ManualInterventionNonTransactionalChange {

    @Execution
    public void execution() {
        // Simple operation that completes successfully
        System.out.println("Executing manual-intervention non-transactional change");
    }
}