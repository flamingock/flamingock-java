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

import io.flamingock.core.kit.audit.AuditEntryTestFactory;
import io.flamingock.cloud.api.request.AuditEntryRequest;
import io.flamingock.cloud.api.request.CloudAuditTxType;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.audit.AuditTxType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HttpAuditWriterMapperTest {

    // Test classes for different recovery strategies
    static class _001__TestManualInterventionChange {
        public void apply() {}
    }

    static class _001__TestDefaultRecoveryChange {
        public void apply() {}
    }

    @Test
    void shouldIncludeTxTypeInRequest() {
        // Given
        AuditEntry auditEntry = AuditEntryTestFactory.createTestAuditEntry("test-change", AuditEntry.Status.APPLIED, AuditTxType.TX_SHARED, _001__TestManualInterventionChange.class);
        CloudAuditTxType txType = auditEntry.getTxType() != null ? auditEntry.getTxType().toCloud() : null;

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
                auditEntry.getSystemChange() != null ? auditEntry.getSystemChange() : false,
                auditEntry.getErrorTrace(),
                txType,
                auditEntry.getTargetSystemId(),
                auditEntry.getOrder(),
                auditEntry.getRecoveryStrategy(),
                auditEntry.getTransactionFlag()
        );

        // Then
        assertEquals(CloudAuditTxType.TX_SHARED, request.getTxStrategy());
    }

    @Test
    void shouldHandleNullTxType() {
        // Given
        AuditEntry auditEntry = AuditEntryTestFactory.createTestAuditEntry("test-change", AuditEntry.Status.APPLIED, null, _001__TestDefaultRecoveryChange.class);
        CloudAuditTxType txType = auditEntry.getTxType() != null ? auditEntry.getTxType().toCloud() : null;

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
                auditEntry.getSystemChange() != null ? auditEntry.getSystemChange() : false,
                auditEntry.getErrorTrace(),
                txType,
                auditEntry.getTargetSystemId(),
                auditEntry.getOrder(),
                auditEntry.getRecoveryStrategy(),
                auditEntry.getTransactionFlag()
        );

        // Then
        assertEquals(CloudAuditTxType.NON_TX, request.getTxStrategy());
    }
}