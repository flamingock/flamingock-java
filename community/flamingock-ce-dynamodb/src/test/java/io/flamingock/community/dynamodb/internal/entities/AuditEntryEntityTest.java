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
import io.flamingock.internal.common.core.targets.operations.OperationType;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

class AuditEntryEntityTest {

    @Test
    void shouldConvertToAndFromAuditEntryWithOperationType() {
        // Given
        AuditEntry original = createTestAuditEntry(OperationType.TX_NON_SYNC);

        // When
        AuditEntryEntity entity = new AuditEntryEntity(original);
        AuditEntry converted = entity.toAuditEntry();

        // Then
        assertEquals(OperationType.TX_NON_SYNC, converted.getOperationType());
        assertEquals(original.getExecutionId(), converted.getExecutionId());
        assertEquals(original.getTaskId(), converted.getTaskId());
        assertEquals(original.getAuthor(), converted.getAuthor());
        assertEquals(original.getState(), converted.getState());
    }

    @Test
    void shouldHandleNullOperationType() {
        // Given
        AuditEntry original = createTestAuditEntry(null);

        // When
        AuditEntryEntity entity = new AuditEntryEntity(original);
        AuditEntry converted = entity.toAuditEntry();

        // Then
        assertNull(converted.getOperationType());
    }

    @Test
    void shouldHandleOperationTypeSetterAndGetter() {
        // Given
        AuditEntryEntity entity = new AuditEntryEntity();

        // When - set valid operation type
        entity.setOperationType(OperationType.TX_AUDIT_STORE_SYNC.name());

        // Then
        assertEquals(OperationType.TX_AUDIT_STORE_SYNC.name(), entity.getOperationType());
    }

    @Test
    void shouldHandleNullOperationTypeSetterAndGetter() {
        // Given
        AuditEntryEntity entity = new AuditEntryEntity();

        // When - set null operation type
        entity.setOperationType(null);

        // Then
        assertNull(entity.getOperationType());
    }

    @Test
    void shouldHandleAllOperationTypes() {
        for (OperationType operationType : OperationType.values()) {
            // Given
            AuditEntry original = createTestAuditEntry(operationType);

            // When
            AuditEntryEntity entity = new AuditEntryEntity(original);
            AuditEntry converted = entity.toAuditEntry();

            // Then
            assertEquals(operationType, converted.getOperationType(), 
                "Failed for OperationType: " + operationType);
        }
    }

    private AuditEntry createTestAuditEntry(OperationType operationType) {
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
                operationType
        );
    }
}