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
package io.flamingock.internal.common.couchbase;

import com.couchbase.client.java.json.JsonObject;
import io.flamingock.api.annotations.ChangeUnit;
import io.flamingock.api.annotations.Execution;
import io.flamingock.api.annotations.Recovery;
import io.flamingock.core.kit.audit.AuditEntryTestFactory;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.audit.AuditTxType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CouchbaseAuditMapperTest {

    private final CouchbaseAuditMapper mapper = new CouchbaseAuditMapper();

    // Test classes for different recovery strategies
    @ChangeUnit(id = "test-manual", order = "001")
    @Recovery(strategy = Recovery.RecoveryStrategy.MANUAL_INTERVENTION)
    static class TestManualInterventionChangeUnit {
        @Execution
        public void execute() {}
    }

    @ChangeUnit(id = "test-always-retry", order = "001") 
    @Recovery(strategy = Recovery.RecoveryStrategy.ALWAYS_RETRY)
    static class TestAlwaysRetryChangeUnit {
        @Execution
        public void execute() {}
    }

    @ChangeUnit(id = "test-default", order = "001")
    static class TestDefaultRecoveryChangeUnit {
        @Execution
        public void execute() {}
    }

    @Test
    void shouldSerializeAndDeserializeTxType() {
        // Given
        AuditEntry original = AuditEntryTestFactory.createTestAuditEntry("test-change", AuditEntry.Status.EXECUTED, AuditTxType.TX_SHARED, TestManualInterventionChangeUnit.class);

        // When
        JsonObject document = mapper.toDocument(original);
        AuditEntry deserialized = mapper.fromDocument(document);

        // Then
        assertEquals(AuditTxType.TX_SHARED, deserialized.getTxType());
    }

    @Test
    void shouldHandleNullTxType() {
        // Given
        AuditEntry original = AuditEntryTestFactory.createTestAuditEntry("test-change", AuditEntry.Status.EXECUTED, null, TestDefaultRecoveryChangeUnit.class);

        // When
        JsonObject document = mapper.toDocument(original);
        AuditEntry deserialized = mapper.fromDocument(document);

        // Then
        assertEquals(AuditTxType.NON_TX, deserialized.getTxType());
    }

    @Test
    void shouldSerializeAndDeserializeTargetSystemId() {
        // Given
        String expectedTargetSystemId = "custom-target-system";
        AuditEntry original = AuditEntryTestFactory.createTestAuditEntry("test-change", AuditEntry.Status.EXECUTED, AuditTxType.TX_SHARED, expectedTargetSystemId, TestManualInterventionChangeUnit.class);

        // When
        JsonObject document = mapper.toDocument(original);
        AuditEntry deserialized = mapper.fromDocument(document);

        // Then
        assertEquals(expectedTargetSystemId, deserialized.getTargetSystemId());
    }

    @Test
    void shouldHandleNullTargetSystemId() {
        // Given
        AuditEntry original = AuditEntryTestFactory.createTestAuditEntry("test-change", AuditEntry.Status.EXECUTED, AuditTxType.NON_TX, null, TestDefaultRecoveryChangeUnit.class);

        // When
        JsonObject document = mapper.toDocument(original);
        AuditEntry deserialized = mapper.fromDocument(document);

        // Then
        assertNull(deserialized.getTargetSystemId());
    }

    @Test
    void shouldHandleAllTxTypes() {
        for (AuditTxType txType : AuditTxType.values()) {
            // Given
            AuditEntry original = AuditEntryTestFactory.createTestAuditEntry("test-change", AuditEntry.Status.EXECUTED, txType, TestManualInterventionChangeUnit.class);

            // When
            JsonObject document = mapper.toDocument(original);
            AuditEntry deserialized = mapper.fromDocument(document);

            // Then
            assertEquals(txType, deserialized.getTxType(),
                "Failed for TxType: " + txType);
        }
    }
}