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

import io.flamingock.api.annotations.Recovery;
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
 * // Create an audit entry for recovery testing with specific ChangeUnit class
 * AuditEntry entry = AuditEntryTestFactory.createTestAuditEntry(
 *     "my-change-id",
 *     AuditEntry.Status.STARTED,
 *     AuditTxType.NON_TX,
 *     MyChangeUnit.class
 * );
 * testKit.getAuditStorage().addAuditEntry(entry);
 * }</pre>
 */
public class AuditEntryTestFactory {

    /**
     * Extracts the recovery strategy from a ChangeUnit class using reflection.
     * 
     * @param changeUnitClass the ChangeUnit class to extract recovery strategy from
     * @return the recovery strategy from @Recovery annotation, or MANUAL_INTERVENTION if not present
     */
    public static Recovery.RecoveryStrategy extractRecoveryStrategy(Class<?> changeUnitClass) {
        if (changeUnitClass != null && changeUnitClass.isAnnotationPresent(Recovery.class)) {
            Recovery recoveryAnnotation = changeUnitClass.getAnnotation(Recovery.class);
            return recoveryAnnotation.strategy();
        }
        return Recovery.RecoveryStrategy.MANUAL_INTERVENTION;
    }

    /**
     * Creates a test audit entry with specific state and transaction type, extracting recovery strategy from the ChangeUnit class.
     *
     * <p>This method creates a fully populated AuditEntry suitable for testing
     * scenarios, particularly recovery testing where specific audit states need
     * to be pre-inserted into storage. The recovery strategy is automatically
     * extracted from the ChangeUnit class's @Recovery annotation.</p>
     *
     * @param changeId the change ID for the audit entry (typically the @ChangeUnit id)
     * @param status   the audit status (STARTED, APPLIED, EXECUTION_FAILED, etc.)
     * @param txType   the transaction type (NON_TX, TX_SHARED, etc.)
     * @param changeUnitClass the ChangeUnit class to extract recovery strategy from
     * @return a properly configured AuditEntry for testing
     */
    public static AuditEntry createTestAuditEntry(String changeId, AuditEntry.Status status, AuditTxType txType, Class<?> changeUnitClass) {
        Recovery.RecoveryStrategy recoveryStrategy = extractRecoveryStrategy(changeUnitClass);
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
                txType,                       // txType
                "test-target-system",         // targetSystemId
                "001",                        // order
                recoveryStrategy               // recoveryStrategy
        );
    }

    /**
     * @deprecated Use {@link #createTestAuditEntry(String, AuditEntry.Status, AuditTxType, Class)} instead.
     * Creates a test audit entry with MANUAL_INTERVENTION recovery strategy.
     */
    @Deprecated
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
                txType,                       // txType
                "test-target-system",         // targetSystemId
                "001",                        // order
                Recovery.RecoveryStrategy.MANUAL_INTERVENTION          // recoveryStrategy
        );
    }

    /**
     * Creates a test audit entry with NON_TX transaction type, extracting recovery strategy from the ChangeUnit class.
     *
     * <p>Convenience method for the common case of creating non-transactional
     * audit entries for testing.</p>
     *
     * @param changeId the change ID for the audit entry
     * @param status   the audit status
     * @param changeUnitClass the ChangeUnit class to extract recovery strategy from
     * @return a non-transactional AuditEntry for testing
     */
    public static AuditEntry createNonTxTestAuditEntry(String changeId, AuditEntry.Status status, Class<?> changeUnitClass) {
        return createTestAuditEntry(changeId, status, AuditTxType.NON_TX, changeUnitClass);
    }

    /**
     * @deprecated Use {@link #createNonTxTestAuditEntry(String, AuditEntry.Status, Class)} instead.
     */
    @Deprecated
    public static AuditEntry createNonTxTestAuditEntry(String changeId, AuditEntry.Status status) {
        return createTestAuditEntry(changeId, status, AuditTxType.NON_TX);
    }

    /**
     * Creates a test audit entry with TX_SHARED transaction type, extracting recovery strategy from the ChangeUnit class.
     *
     * <p>Convenience method for creating shared transactional audit entries
     * where the target system is the same as the audit store.</p>
     *
     * @param changeId the change ID for the audit entry
     * @param status   the audit status
     * @param changeUnitClass the ChangeUnit class to extract recovery strategy from
     * @return a shared transactional AuditEntry for testing
     */
    public static AuditEntry createSharedTxTestAuditEntry(String changeId, AuditEntry.Status status, Class<?> changeUnitClass) {
        return createTestAuditEntry(changeId, status, AuditTxType.TX_SHARED, changeUnitClass);
    }

    /**
     * @deprecated Use {@link #createSharedTxTestAuditEntry(String, AuditEntry.Status, Class)} instead.
     */
    @Deprecated
    public static AuditEntry createSharedTxTestAuditEntry(String changeId, AuditEntry.Status status) {
        return createTestAuditEntry(changeId, status, AuditTxType.TX_SHARED);
    }

    /**
     * Creates a test audit entry with TX_SEPARATE_WITH_MARKER transaction type, extracting recovery strategy from the ChangeUnit class.
     *
     * <p>Convenience method for creating separate transactional audit entries
     * with markers, where the target system differs from the audit store.</p>
     *
     * @param changeId the change ID for the audit entry
     * @param status   the audit status
     * @param changeUnitClass the ChangeUnit class to extract recovery strategy from
     * @return a separate transactional AuditEntry with marker for testing
     */
    public static AuditEntry createSeparateTxWithMarkerTestAuditEntry(String changeId, AuditEntry.Status status, Class<?> changeUnitClass) {
        return createTestAuditEntry(changeId, status, AuditTxType.TX_SEPARATE_WITH_MARKER, changeUnitClass);
    }

    /**
     * @deprecated Use {@link #createSeparateTxWithMarkerTestAuditEntry(String, AuditEntry.Status, Class)} instead.
     */
    @Deprecated
    public static AuditEntry createSeparateTxWithMarkerTestAuditEntry(String changeId, AuditEntry.Status status) {
        return createTestAuditEntry(changeId, status, AuditTxType.TX_SEPARATE_WITH_MARKER);
    }

    /**
     * Creates a test audit entry with specific state, transaction type, and target system ID, extracting recovery strategy from the ChangeUnit class.
     *
     * <p>This method provides full control over all key parameters including the
     * target system ID, useful for testing scenarios where specific target systems
     * need to be verified in audit logs.</p>
     *
     * @param changeId       the change ID for the audit entry (typically the @ChangeUnit id)
     * @param status         the audit status (STARTED, APPLIED, EXECUTION_FAILED, etc.)
     * @param txType         the transaction type (NON_TX, TX_SHARED, etc.)
     * @param targetSystemId the target system identifier
     * @param changeUnitClass the ChangeUnit class to extract recovery strategy from
     * @return a properly configured AuditEntry for testing with specified target system
     */
    public static AuditEntry createTestAuditEntry(String changeId, AuditEntry.Status status, AuditTxType txType, String targetSystemId, Class<?> changeUnitClass) {
        Recovery.RecoveryStrategy recoveryStrategy = extractRecoveryStrategy(changeUnitClass);
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
                txType,                       // txType
                targetSystemId,               // targetSystemId
                "001",                        // order
                recoveryStrategy              // recoveryStrategy
        );
    }

    /**
     * @deprecated Use {@link #createTestAuditEntry(String, AuditEntry.Status, AuditTxType, String, Class)} instead.
     */
    @Deprecated
    public static AuditEntry createTestAuditEntry(String changeId, AuditEntry.Status status, AuditTxType txType, String targetSystemId) {
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
                txType,                       // txType
                targetSystemId,               // targetSystemId
                "001",                        // order
                Recovery.RecoveryStrategy.MANUAL_INTERVENTION // recoveryStrategy
        );
    }

    /**
     * Creates a test audit entry with specific state, transaction type, and recovery strategy.
     *
     * @param changeId         the unique identifier for the change unit
     * @param status           the audit entry status (STARTED, APPLIED, etc.)
     * @param txType           the transaction type (NON_TX, TX_SHARED, etc.)
     * @param recoveryStrategy the recovery strategy for this change unit
     * @return a properly configured AuditEntry for testing with specified recovery strategy
     */
    public static AuditEntry createTestAuditEntryWithRecoveryStrategy(String changeId, AuditEntry.Status status, AuditTxType txType, String recoveryStrategy) {
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
                txType,                       // txType
                null,                         // targetSystemId
                "001",                        // order
                Recovery.RecoveryStrategy.MANUAL_INTERVENTION // recoveryStrategy
        );
    }
}