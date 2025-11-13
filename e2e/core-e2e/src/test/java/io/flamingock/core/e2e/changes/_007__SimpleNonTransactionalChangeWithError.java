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

import io.flamingock.api.annotations.*;
import io.flamingock.core.e2e.helpers.Counter;

import javax.inject.Named;

/**
 * Simple non-transactional change for testing core execution strategies.
 * Does not require any external dependencies.
 */
@Change(id = "test1-non-tx-change", transactional = false, author = "aperezdieppa")
@TargetSystem(id = "kafka")
public class _007__SimpleNonTransactionalChangeWithError {

    @Apply
    public void apply(@NonLockGuarded Counter counter) {
        counter.setExecuted(true);
        throw new RuntimeException("Intentional failure");
    }

    @Rollback
    public void rollback(@NonLockGuarded Counter counter) {
        counter.setRollbacked(true);
        System.out.println("Rolling back failing transactional change");
    }
}