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

import io.flamingock.api.annotations.Change;
import io.flamingock.api.annotations.Apply;
import io.flamingock.api.annotations.Recovery;
import io.flamingock.api.RecoveryStrategy;
import io.flamingock.core.kit.audit.AuditEntryTestFactory;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.audit.AuditTxType;
import io.flamingock.internal.util.dynamodb.entities.AuditEntryEntity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AuditEntryEntityTest {

    // Test classes for different recovery strategies
    @Change(id = "test-manual", order = "001", author = "aperezdieppa")
    @Recovery(strategy = RecoveryStrategy.MANUAL_INTERVENTION)
    static class TestManualInterventionChange {
        @Apply
        public void execute() {}
    }

    @Change(id = "test-default", order = "001", author = "aperezdieppa")
    static class TestDefaultRecoveryChange {
        @Apply
        public void execute() {}
    }

    @Test
    void shouldConvertToAndFromAuditEntryWithTxType() {
        // Given
        AuditEntry original = AuditEntryTestFactory.createTestAuditEntry("test-change", AuditEntry.Status.APPLIED, AuditTxType.TX_SEPARATE_NO_MARKER, TestManualInterventionChange.class);

        // When
        AuditEntryEntity entity = new AuditEntryEntity(original);
        AuditEntry converted = entity.toAuditEntry();

        // Then
        assertEquals(AuditTxType.TX_SEPARATE_NO_MARKER, converted.getTxType());
        assertEquals(original.getExecutionId(), converted.getExecutionId());
        assertEquals(original.getTaskId(), converted.getTaskId());
        assertEquals(original.getAuthor(), converted.getAuthor());
        assertEquals(original.getState(), converted.getState());
    }

    @Test
    void shouldReturnNonTxWhenNull() {
        // Given
        AuditEntry original = AuditEntryTestFactory.createTestAuditEntry("test-change", AuditEntry.Status.APPLIED, null, TestDefaultRecoveryChange.class);

        // When
        AuditEntryEntity entity = new AuditEntryEntity(original);
        AuditEntry converted = entity.toAuditEntry();

        // Then
        assertEquals(AuditTxType.NON_TX, converted.getTxType());
    }

    @Test
    void shouldHandleTxTypeSetterAndGetter() {
        // Given
        AuditEntryEntity entity = new AuditEntryEntity();

        // When - set valid operation type
        entity.setTxType(AuditTxType.TX_SEPARATE_WITH_MARKER.name());

        // Then
        assertEquals(AuditTxType.TX_SEPARATE_WITH_MARKER.name(), entity.getTxType());
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
        for (AuditTxType txStrategy : AuditTxType.values()) {
            // Given
            AuditEntry original = AuditEntryTestFactory.createTestAuditEntry("test-change", AuditEntry.Status.APPLIED, txStrategy, TestManualInterventionChange.class);

            // When
            AuditEntryEntity entity = new AuditEntryEntity(original);
            AuditEntry converted = entity.toAuditEntry();

            // Then
            assertEquals(txStrategy, converted.getTxType(),
                "Failed for TxType: " + txStrategy);
        }
    }

    @Test
    void shouldConvertToAndFromAuditEntryWithTargetSystemId() {
        // Given
        String expectedTargetSystemId = "custom-target-system";
        AuditEntry original = AuditEntryTestFactory.createTestAuditEntry("test-change", AuditEntry.Status.APPLIED, AuditTxType.TX_SHARED, expectedTargetSystemId, TestManualInterventionChange.class);

        // When
        AuditEntryEntity entity = new AuditEntryEntity(original);
        AuditEntry converted = entity.toAuditEntry();

        // Then
        assertEquals(expectedTargetSystemId, converted.getTargetSystemId());
        assertEquals(original.getExecutionId(), converted.getExecutionId());
        assertEquals(original.getTaskId(), converted.getTaskId());
        assertEquals(original.getAuthor(), converted.getAuthor());
        assertEquals(original.getState(), converted.getState());
    }

    @Test
    void shouldHandleNullTargetSystemId() {
        // Given
        AuditEntry original = AuditEntryTestFactory.createTestAuditEntry("test-change", AuditEntry.Status.APPLIED, AuditTxType.NON_TX, null, TestDefaultRecoveryChange.class);

        // When
        AuditEntryEntity entity = new AuditEntryEntity(original);
        AuditEntry converted = entity.toAuditEntry();

        // Then
        assertNull(converted.getTargetSystemId());
    }
}