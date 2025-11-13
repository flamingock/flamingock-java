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
import io.flamingock.api.RecoveryStrategy;
import io.flamingock.api.annotations.Change;
import io.flamingock.api.annotations.Apply;
import io.flamingock.api.annotations.Recovery;
import io.flamingock.core.kit.audit.AuditEntryTestFactory;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.audit.AuditTxType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CouchbaseAuditMapperTest {

    private final CouchbaseAuditMapper mapper = new CouchbaseAuditMapper();

    // Test classes for different recovery strategies
    @Change(id = "test-manual", author = "aperezdieppa")
    @Recovery(strategy = RecoveryStrategy.MANUAL_INTERVENTION)
    static class _001__TestManualInterventionChange {
        @Apply
        public void apply() {}
    }

    @Change(id = "test-always-retry", author = "aperezdieppa")
    @Recovery(strategy = RecoveryStrategy.ALWAYS_RETRY)
    static class _001__TestAlwaysRetryChange {
        @Apply
        public void apply() {}
    }

    @Change(id = "test-default", author = "aperezdieppa")
    static class _001__TestDefaultRecoveryChange {
        @Apply
        public void apply() {}
    }

    @Test
    void shouldSerializeAndDeserializeTxType() {
        // Given
        AuditEntry original = AuditEntryTestFactory.createTestAuditEntry("test-change", AuditEntry.Status.APPLIED, AuditTxType.TX_SHARED, _001__TestManualInterventionChange.class);

        // When
        JsonObject document = mapper.toDocument(original);
        AuditEntry deserialized = mapper.fromDocument(document);

        // Then
        assertEquals(AuditTxType.TX_SHARED, deserialized.getTxType());
    }

    @Test
    void shouldHandleNullTxType() {
        // Given
        AuditEntry original = AuditEntryTestFactory.createTestAuditEntry("test-change", AuditEntry.Status.APPLIED, null, _001__TestDefaultRecoveryChange.class);

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
        AuditEntry original = AuditEntryTestFactory.createTestAuditEntry("test-change", AuditEntry.Status.APPLIED, AuditTxType.TX_SHARED, expectedTargetSystemId, _001__TestManualInterventionChange.class);

        // When
        JsonObject document = mapper.toDocument(original);
        AuditEntry deserialized = mapper.fromDocument(document);

        // Then
        assertEquals(expectedTargetSystemId, deserialized.getTargetSystemId());
    }

    @Test
    void shouldHandleNullTargetSystemId() {
        // Given
        AuditEntry original = AuditEntryTestFactory.createTestAuditEntry("test-change", AuditEntry.Status.APPLIED, AuditTxType.NON_TX, null, _001__TestDefaultRecoveryChange.class);

        // When
        JsonObject document = mapper.toDocument(original);
        AuditEntry deserialized = mapper.fromDocument(document);

        // Then
        assertNull(deserialized.getTargetSystemId());
    }

    @Test
    void shouldHandleAllTxTypes() {
        for (AuditTxType txStrategy : AuditTxType.values()) {
            // Given
            AuditEntry original = AuditEntryTestFactory.createTestAuditEntry("test-change", AuditEntry.Status.APPLIED, txStrategy, _001__TestManualInterventionChange.class);

            // When
            JsonObject document = mapper.toDocument(original);
            AuditEntry deserialized = mapper.fromDocument(document);

            // Then
            assertEquals(txStrategy, deserialized.getTxType(),
                "Failed for TxType: " + txStrategy);
        }
    }
}