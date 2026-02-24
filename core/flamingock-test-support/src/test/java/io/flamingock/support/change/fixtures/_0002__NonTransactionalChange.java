/*
 * Copyright 2026 Flamingock (https://www.flamingock.io)
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
package io.flamingock.support.change.fixtures;

import io.flamingock.api.annotations.Apply;
import io.flamingock.api.annotations.Change;
import io.flamingock.api.annotations.TargetSystem;

/**
 * Fixture change for ChangeValidatorTest.
 * transactional=false, @TargetSystem present, no @Recovery (default = MANUAL_INTERVENTION),
 * no @Rollback.
 * order = "0002" (from class name prefix).
 */
@Change(id = "non-transactional", author = "test-author", transactional = false)
@TargetSystem(id = "kafka")
public class _0002__NonTransactionalChange {

    @Apply
    public void apply() {
    }
}
