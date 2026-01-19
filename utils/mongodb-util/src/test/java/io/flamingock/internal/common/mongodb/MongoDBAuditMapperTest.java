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
package io.flamingock.internal.common.mongodb;

import io.flamingock.api.RecoveryStrategy;
import io.flamingock.api.annotations.Change;
import io.flamingock.api.annotations.Apply;
import io.flamingock.api.annotations.Recovery;
import io.flamingock.core.kit.audit.AuditEntryTestFactory;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.audit.AuditTxType;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MongoDBAuditMapperTest {

    private final MongoDBAuditMapper<TestDocumentWrapper> mapper = 
        new MongoDBAuditMapper<>(TestDocumentWrapper::new);

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
        TestDocumentWrapper document = mapper.toDocument(original);
        AuditEntry deserialized = mapper.fromDocument(document);

        // Then
        assertEquals(AuditTxType.TX_SHARED, deserialized.getTxType());
    }

    @Test
    void shouldHandleNullTxType() {
        // Given
        AuditEntry original = AuditEntryTestFactory.createTestAuditEntry("test-change", AuditEntry.Status.APPLIED, null, _001__TestDefaultRecoveryChange.class);

        // When
        TestDocumentWrapper document = mapper.toDocument(original);
        AuditEntry deserialized = mapper.fromDocument(document);

        // Then
        assertEquals(AuditTxType.NON_TX, deserialized.getTxType());
    }

    @Test
    void shouldHandleInvalidTxTypeInDocument() {
        // Given
        TestDocumentWrapper document = new TestDocumentWrapper();
        document.append("executionId", "test-execution");
        document.append("stageId", "test-stage");
        document.append("taskId", "test-task");
        document.append("author", "test-author");
        document.append("state", AuditEntry.Status.APPLIED.name());
        document.append("type", AuditEntry.ChangeType.STANDARD_CODE.name());
        document.append("txStrategy", "INVALID_OPERATION_TYPE");

        // When
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
        TestDocumentWrapper document = mapper.toDocument(original);
        AuditEntry deserialized = mapper.fromDocument(document);

        // Then
        assertEquals(expectedTargetSystemId, deserialized.getTargetSystemId());
    }

    @Test
    void shouldHandleNullTargetSystemId() {
        // Given
        AuditEntry original = AuditEntryTestFactory.createTestAuditEntry("test-change", AuditEntry.Status.APPLIED, AuditTxType.NON_TX, null, _001__TestDefaultRecoveryChange.class);

        // When
        TestDocumentWrapper document = mapper.toDocument(original);
        AuditEntry deserialized = mapper.fromDocument(document);

        // Then
        assertNull(deserialized.getTargetSystemId());
    }

    @Test
    void shouldSerializeAndDeserializeRecoveryStrategy() {
        // Given
        AuditEntry original = AuditEntryTestFactory.createTestAuditEntry("test-change", AuditEntry.Status.APPLIED, AuditTxType.NON_TX, _001__TestAlwaysRetryChange.class);

        // When
        TestDocumentWrapper document = mapper.toDocument(original);
        AuditEntry deserialized = mapper.fromDocument(document);

        // Then
        assertEquals(RecoveryStrategy.ALWAYS_RETRY, deserialized.getRecoveryStrategy());
    }

    @Test
    void shouldHandleNullRecoveryStrategy() {
        // Given
        AuditEntry original = AuditEntryTestFactory.createTestAuditEntry("test-change", AuditEntry.Status.APPLIED, AuditTxType.NON_TX, _001__TestDefaultRecoveryChange.class);

        // When
        TestDocumentWrapper document = mapper.toDocument(original);
        AuditEntry deserialized = mapper.fromDocument(document);

        // Then
        assertEquals(RecoveryStrategy.MANUAL_INTERVENTION, deserialized.getRecoveryStrategy()); // Should default to MANUAL_INTERVENTION
    }

    // Simple test implementation of DocumentHelper
    static class TestDocumentWrapper implements DocumentHelper {
        private final Map<String, Object> data = new HashMap<>();

        @Override
        public DocumentHelper append(String key, Object value) {
            data.put(key, value);
            return this;
        }

        @Override
        public Object get(String key) {
            return data.get(key);
        }

        @Override
        public String getString(String key) {
            Object value = data.get(key);
            return value != null ? value.toString() : null;
        }

        @Override
        public Boolean getBoolean(String key) {
            Object value = data.get(key);
            if (value instanceof Boolean) {
                return (Boolean) value;
            }
            return value != null ? Boolean.valueOf(value.toString()) : null;
        }

        @Override
        public boolean getBoolean(Object key, boolean defaultValue) {
            Object value = data.get(key);
            if (value instanceof Boolean) {
                return (Boolean) value;
            }
            return defaultValue;
        }

        @Override
        public DocumentHelper getWithWrapper(String key) {
            Object value = data.get(key);
            if (value instanceof Map) {
                TestDocumentWrapper wrapper = new TestDocumentWrapper();
                wrapper.data.putAll((Map<String, Object>) value);
                return wrapper;
            }
            return null;
        }

        @Override
        public int size() {
            return data.size();
        }

        @Override
        public boolean containsKey(String key) {
            return data.containsKey(key);
        }
    }
}