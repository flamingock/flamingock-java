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

import javax.inject.Named;

/**
 * Change that receives the target system ID as a dependency to verify
 * that it can be injected via @Named("change.targetSystem.id").
 */
@Change(id = "test9-target-system-id-injection", transactional = false, author = "aperezdieppa")
@TargetSystem(id = "kafka")
public class _009__TargetSystemIdInjectionChange {

    @Apply
    public void apply(@Named("change.targetSystem.id") String targetSystemId, @NonLockGuarded Counter counter) {
        if (targetSystemId != null && !targetSystemId.isEmpty()) {
            counter.setTargetSystemId(targetSystemId);
            counter.setExecuted(true);
            System.out.println("Target system ID successfully injected: " + targetSystemId);
        } else {
            throw new RuntimeException("Target system ID was not injected");
        }
    }
}
