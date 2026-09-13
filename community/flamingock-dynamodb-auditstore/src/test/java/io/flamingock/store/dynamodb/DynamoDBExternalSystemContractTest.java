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
package io.flamingock.store.dynamodb;

import io.flamingock.externalsystem.dynamodb.api.DynamoDBExternalSystem;
import io.flamingock.internal.common.core.transaction.TransactionalExternalSystem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DynamoDBExternalSystemContractTest {

    @Test
    @DisplayName("DynamoDB external systems expose the shared transactional contract")
    void dynamoDBExternalSystemIsTransactional() {
        assertTrue(
                TransactionalExternalSystem.class.isAssignableFrom(DynamoDBExternalSystem.class),
                "DynamoDBExternalSystem must extend TransactionalExternalSystem"
        );
    }
}
