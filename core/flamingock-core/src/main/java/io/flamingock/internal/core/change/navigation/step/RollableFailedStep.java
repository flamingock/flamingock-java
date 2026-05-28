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
package io.flamingock.internal.core.change.navigation.step;

import io.flamingock.internal.core.change.navigation.step.afteraudit.RollableStep;

import java.util.Optional;

public interface RollableFailedStep extends FailedStep {

    /**
     * Returns the rollback step when the failed change declares a rollback, or {@link Optional#empty()}
     * when it doesn't. {@code @RollbackExecution} is optional, so a non-transactional change that fails
     * without declaring one has no rollback to invoke. Callers must branch on the Optional and skip the
     * rollback path (and the manual-rollback audit entry) when absent — the upstream {@code FAILED}
     * audit entry is the truth, and recovery follows the configured {@code RecoveryStrategy}.
     */
    Optional<RollableStep> getRollbackStep();


}
