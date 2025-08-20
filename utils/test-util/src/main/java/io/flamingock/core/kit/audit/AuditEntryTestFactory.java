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
package io.flamingock.core.kit.audit;

import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.audit.AuditTxType;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Factory utility for creating test audit entries in recovery and audit testing scenarios.
 * 
 * <p>This factory provides convenient methods for creating AuditEntry instances with
 * specific states and transaction types for testing purposes, particularly useful in
 * recovery scenario testing where pre-existing audit entries need to be inserted
 * into storage to simulate various system states.</p>
 * 
 * <p><strong>Usage Example:</strong></p>
 * <pre>{@code
 * // Create an audit entry for recovery testing
 * AuditEntry entry = AuditEntryTestFactory.createTestAuditEntry(
 *     "my-change-id", 
 *     AuditEntry.Status.STARTED, 
 *     AuditTxType.NON_TX
 * );
 * testKit.getAuditStorage().addAuditEntry(entry);
 * }</pre>
 */
public class AuditEntryTestFactory {

    /**
     * Creates a test audit entry with specific state and transaction type.
     * 
     * <p>This method creates a fully populated AuditEntry suitable for testing
     * scenarios, particularly recovery testing where specific audit states need
     * to be pre-inserted into storage.</p>
     * 
     * @param changeId the change ID for the audit entry (typically the @ChangeUnit id)
     * @param status the audit status (STARTED, EXECUTED, EXECUTION_FAILED, etc.)
     * @param txType the transaction type (NON_TX, TX_SHARED, etc.)
     * @return a properly configured AuditEntry for testing
     */
    public static AuditEntry createTestAuditEntry(String changeId, AuditEntry.Status status, AuditTxType txType) {
        return new AuditEntry(
            UUID.randomUUID().toString(),  // executionId
            "test-stage",                  // stageId
            changeId,                      // taskId
            "test-author",                 // author
            LocalDateTime.now(),           // timestamp
            status,                        // state
            AuditEntry.ExecutionType.EXECUTION,  // type
            "TestChangeClass",             // className
            "testMethod",                  // methodName
            0L,                           // executionMillis
            "localhost",                  // executionHostname
            null,                         // metadata
            false,                        // systemChange
            null,                         // errorTrace
            txType                        // txType
        );
    }

    /**
     * Creates a test audit entry with NON_TX transaction type.
     * 
     * <p>Convenience method for the common case of creating non-transactional
     * audit entries for testing.</p>
     * 
     * @param changeId the change ID for the audit entry
     * @param status the audit status
     * @return a non-transactional AuditEntry for testing
     */
    public static AuditEntry createNonTxTestAuditEntry(String changeId, AuditEntry.Status status) {
        return createTestAuditEntry(changeId, status, AuditTxType.NON_TX);
    }

    /**
     * Creates a test audit entry with TX_SHARED transaction type.
     * 
     * <p>Convenience method for creating shared transactional audit entries
     * where the target system is the same as the audit store.</p>
     * 
     * @param changeId the change ID for the audit entry
     * @param status the audit status
     * @return a shared transactional AuditEntry for testing
     */
    public static AuditEntry createSharedTxTestAuditEntry(String changeId, AuditEntry.Status status) {
        return createTestAuditEntry(changeId, status, AuditTxType.TX_SHARED);
    }

    /**
     * Creates a test audit entry with TX_SEPARATE_WITH_MARKER transaction type.
     * 
     * <p>Convenience method for creating separate transactional audit entries
     * with markers, where the target system differs from the audit store.</p>
     * 
     * @param changeId the change ID for the audit entry
     * @param status the audit status
     * @return a separate transactional AuditEntry with marker for testing
     */
    public static AuditEntry createSeparateTxWithMarkerTestAuditEntry(String changeId, AuditEntry.Status status) {
        return createTestAuditEntry(changeId, status, AuditTxType.TX_SEPARATE_WITH_MARKER);
    }
}