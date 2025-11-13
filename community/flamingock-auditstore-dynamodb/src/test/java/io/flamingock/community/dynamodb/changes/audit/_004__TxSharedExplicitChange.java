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
import software.amazon.awssdk.enhanced.dynamodb.model.TransactWriteItemsEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

/**
 * Change that produces TX_SHARED txStrategy via explicit same DynamoDbClient.
 * Uses custom DynamoDBTargetSystem with same DynamoDbClient as audit storage.
 */
@TargetSystem(id = "tx-shared-system")
@Change(id = "tx-shared-explicit", author = "test-author")
public class _004__TxSharedExplicitChange {

    @Apply
    public void apply(DynamoDbClient client, TransactWriteItemsEnhancedRequest.Builder writeRequestBuilder) {
        // Transactional execution with explicit same DynamoDbClient - this will be TX_SHARED
        System.out.println("Executing TX_SHARED change via explicit DynamoDBTargetSystem with same DynamoDbClient");
    }
}