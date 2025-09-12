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
package io.flamingock.cloud.audit;

import io.flamingock.api.annotations.Change;
import io.flamingock.api.annotations.Apply;
import io.flamingock.api.annotations.Recovery;
import io.flamingock.core.kit.audit.AuditEntryTestFactory;
import io.flamingock.internal.common.cloud.audit.AuditEntryRequest;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.audit.AuditTxType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HttpAuditWriterMapperTest {

    // Test classes for different recovery strategies
    @Change(id = "test-manual", order = "001")
    @Recovery(strategy = Recovery.RecoveryStrategy.MANUAL_INTERVENTION)
    static class TestManualInterventionChangeUnit {
        @Apply
        public void execute() {}
    }

    @Change(id = "test-default", order = "001")
    static class TestDefaultRecoveryChangeUnit {
        @Apply
        public void execute() {}
    }

    @Test
    void shouldIncludeTxTypeInRequest() {
        // Given
        AuditEntry auditEntry = AuditEntryTestFactory.createTestAuditEntry("test-change", AuditEntry.Status.APPLIED, AuditTxType.TX_SHARED, TestManualInterventionChangeUnit.class);

        // When
        AuditEntryRequest request = new AuditEntryRequest(
                auditEntry.getStageId(),
                auditEntry.getTaskId(),
                auditEntry.getAuthor(),
                System.currentTimeMillis(),
                auditEntry.getState().toRequestStatus(),
                auditEntry.getType().toRequestExecutionType(),
                auditEntry.getClassName(),
                auditEntry.getMethodName(),
                auditEntry.getExecutionMillis(),
                auditEntry.getExecutionHostname(),
                auditEntry.getMetadata(),
                auditEntry.getSystemChange(),
                auditEntry.getErrorTrace(),
                auditEntry.getTxType(),
                auditEntry.getTargetSystemId(),
                auditEntry.getOrder(),
                auditEntry.getRecoveryStrategy()
        );

        // Then
        assertEquals(AuditTxType.TX_SHARED, request.getTxType());
    }

    @Test
    void shouldHandleNullTxType() {
        // Given
        AuditEntry auditEntry = AuditEntryTestFactory.createTestAuditEntry("test-change", AuditEntry.Status.APPLIED, null, TestDefaultRecoveryChangeUnit.class);

        // When
        AuditEntryRequest request = new AuditEntryRequest(
                auditEntry.getStageId(),
                auditEntry.getTaskId(),
                auditEntry.getAuthor(),
                System.currentTimeMillis(),
                auditEntry.getState().toRequestStatus(),
                auditEntry.getType().toRequestExecutionType(),
                auditEntry.getClassName(),
                auditEntry.getMethodName(),
                auditEntry.getExecutionMillis(),
                auditEntry.getExecutionHostname(),
                auditEntry.getMetadata(),
                auditEntry.getSystemChange(),
                auditEntry.getErrorTrace(),
                auditEntry.getTxType(),
                auditEntry.getTargetSystemId(),
                auditEntry.getOrder(),
                auditEntry.getRecoveryStrategy()
        );

        // Then
        assertEquals(AuditTxType.NON_TX, request.getTxType());
    }
}