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

import io.flamingock.api.annotations.Change;
import io.flamingock.api.annotations.Apply;
import io.flamingock.api.annotations.TargetSystem;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

/**
 * Change that produces NON_TX txStrategy via transactional=false.
 * Used for testing audit persistence of transaction type determination.
 */
@TargetSystem(id = "dynamodb")
@Change(id = "non-tx-transactional-false", transactional = false, author = "test-author")
public class _001__NonTxTransactionalFalseChange {

    @Apply
    public void execution(DynamoDbClient client) {
        // Simple execution - this will be NON_TX due to transactional=false
        System.out.println("Executing NON_TX change via transactional=false");
    }
}