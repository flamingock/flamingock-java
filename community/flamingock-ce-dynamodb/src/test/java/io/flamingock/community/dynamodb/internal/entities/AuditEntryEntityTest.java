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
package io.flamingock.community.dynamodb.internal.entities;

import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.audit.AuditTxType;
import io.flamingock.internal.common.core.targets.OperationType;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

class AuditEntryEntityTest {

    @Test
    void shouldConvertToAndFromAuditEntryWithOperationType() {
        // Given
        AuditEntry original = createTestAuditEntry(AuditTxType.TX_NON_SYNC);

        // When
        AuditEntryEntity entity = new AuditEntryEntity(original);
        AuditEntry converted = entity.toAuditEntry();

        // Then
        assertEquals(AuditTxType.TX_NON_SYNC, converted.getTxType());
        assertEquals(original.getExecutionId(), converted.getExecutionId());
        assertEquals(original.getTaskId(), converted.getTaskId());
        assertEquals(original.getAuthor(), converted.getAuthor());
        assertEquals(original.getState(), converted.getState());
    }

    @Test
    void shouldReturnNonTxWhenNull() {
        // Given
        AuditEntry original = createTestAuditEntry(null);

        // When
        AuditEntryEntity entity = new AuditEntryEntity(original);
        AuditEntry converted = entity.toAuditEntry();

        // Then
        assertEquals(AuditTxType.NON_TX, converted.getTxType());
    }

    @Test
    void shouldHandleOperationTypeSetterAndGetter() {
        // Given
        AuditEntryEntity entity = new AuditEntryEntity();

        // When - set valid operation type
        entity.setTxType(OperationType.TX_AUDIT_STORE_SYNC.name());

        // Then
        assertEquals(OperationType.TX_AUDIT_STORE_SYNC.name(), entity.getTxType());
    }

    @Test
    void shouldReturnNonTxTypeSetterAndGetter() {
        // Given
        AuditEntryEntity entity = new AuditEntryEntity();

        // When - set null operation type
        entity.setTxType(null);

        // Then
        assertEquals(AuditTxType.NON_TX.name(), entity.getTxType());
    }

    @Test
    void shouldHandleAllTxTypes() {
        for (AuditTxType txType : AuditTxType.values()) {
            // Given
            AuditEntry original = createTestAuditEntry(txType);

            // When
            AuditEntryEntity entity = new AuditEntryEntity(original);
            AuditEntry converted = entity.toAuditEntry();

            // Then
            assertEquals(txType, converted.getTxType(),
                "Failed for TxType: " + txType);
        }
    }

    private AuditEntry createTestAuditEntry(AuditTxType txType) {
        return new AuditEntry(
                "test-execution",
                "test-stage", 
                "test-task",
                "test-author",
                LocalDateTime.now(),
                AuditEntry.Status.EXECUTED,
                AuditEntry.ExecutionType.EXECUTION,
                "TestClass",
                "testMethod",
                100L,
                "localhost",
                new HashMap<>(),
                false,
                null,
                txType
        );
    }
}