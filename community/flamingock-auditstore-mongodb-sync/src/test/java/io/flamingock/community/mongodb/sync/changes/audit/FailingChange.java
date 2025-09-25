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
 * Change that intentionally fails to test error audit scenarios.
 * Used for testing audit persistence of error fields like errorTrace.
 */
@TargetSystem(id = "mongodb")
@Change(id = "failing-change", order = "006", transactional = false, author = "aperezdieppa")
public class FailingChange {

    @Apply
    public void execution() {
        // Intentionally throw exception to test error audit
        throw new RuntimeException("Intentional failure for audit testing");
    }
}
