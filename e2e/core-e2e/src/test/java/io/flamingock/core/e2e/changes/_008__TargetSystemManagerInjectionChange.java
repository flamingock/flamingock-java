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
import io.flamingock.api.annotations.NonLockGuarded;
import io.flamingock.api.annotations.TargetSystem;
import io.flamingock.core.e2e.helpers.Counter;
import io.flamingock.internal.core.external.targets.TargetSystemManager;

/**
 * Change that receives TargetSystemManager as a dependency to verify
 * that it can be injected into changes.
 */
@Change(id = "test8-target-system-manager-injection", transactional = false, author = "aperezdieppa")
@TargetSystem(id = "kafka")
public class _008__TargetSystemManagerInjectionChange {

    @Apply
    public void apply(TargetSystemManager targetSystemManager, @NonLockGuarded Counter counter) {
        if (targetSystemManager != null) {
            counter.setExecuted(true);
            System.out.println("TargetSystemManager successfully injected");
        } else {
            throw new RuntimeException("TargetSystemManager was not injected");
        }
    }
}
