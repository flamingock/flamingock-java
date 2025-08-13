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

import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.audit.AuditTxType;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MongoDBAuditMapperTest {

    private final MongoDBAuditMapper<TestDocumentWrapper> mapper = 
        new MongoDBAuditMapper<>(TestDocumentWrapper::new);

    @Test
    void shouldSerializeAndDeserializeOperationType() {
        // Given
        AuditEntry original = createTestAuditEntry(AuditTxType.TX_AUDIT_STORE_SHARED);

        // When
        TestDocumentWrapper document = mapper.toDocument(original);
        AuditEntry deserialized = mapper.fromDocument(document);

        // Then
        assertEquals(AuditTxType.TX_AUDIT_STORE_SHARED, deserialized.getTxType());
    }

    @Test
    void shouldHandleNullOperationType() {
        // Given
        AuditEntry original = createTestAuditEntry(null);

        // When
        TestDocumentWrapper document = mapper.toDocument(original);
        AuditEntry deserialized = mapper.fromDocument(document);

        // Then
        assertEquals(AuditTxType.NON_TX, deserialized.getTxType());
    }

    @Test
    void shouldHandleInvalidOperationTypeInDocument() {
        // Given
        TestDocumentWrapper document = new TestDocumentWrapper();
        document.append("executionId", "test-execution");
        document.append("stageId", "test-stage");
        document.append("taskId", "test-task");
        document.append("author", "test-author");
        document.append("state", AuditEntry.Status.EXECUTED.name());
        document.append("type", AuditEntry.ExecutionType.EXECUTION.name());
        document.append("operationType", "INVALID_OPERATION_TYPE");

        // When
        AuditEntry deserialized = mapper.fromDocument(document);

        // Then
        assertEquals(AuditTxType.NON_TX, deserialized.getTxType());
    }

    private AuditEntry createTestAuditEntry(AuditTxType operationType) {
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