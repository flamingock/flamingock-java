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
 * ChangeUnit that produces TX_SEPARATE_NO_MARKER txType via different DynamoDbClient.
 * Uses custom DynamoDBTargetSystem with different DynamoDbClient than audit storage.
 */
@TargetSystem(id = "mongo-system")
@Change(id = "tx-separate-no-marker", order = "005", author = "test-author")
public class TxSeparateAndSameMongoClientChange {

    @Apply
    public void execution(DynamoDbClient client, TransactWriteItemsEnhancedRequest.Builder writeRequestBuilder) {
        // Transactional execution with different DynamoDbClient - this will be TX_SEPARATE_NO_MARKER
        System.out.println("Executing TX_SEPARATE_NO_MARKER change via DynamoDBTargetSystem with different DynamoDbClient");
    }
}
