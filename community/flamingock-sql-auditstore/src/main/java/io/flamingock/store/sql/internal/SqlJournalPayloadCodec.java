/*
 * Copyright 2026 Flamingock (https://www.flamingock.io)
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
package io.flamingock.store.sql.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.flamingock.api.RecoveryStrategy;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.audit.AuditTxType;
import io.flamingock.internal.util.JsonObjectMapper;

import java.time.LocalDateTime;

/**
 * Serializes the SQL journal's currently supported {@link AuditEntry} payload locally as JSON.
 */
final class SqlJournalPayloadCodec {

    private static final ObjectMapper OBJECT_MAPPER = JsonObjectMapper.DEFAULT_INSTANCE;

    private SqlJournalPayloadCodec() {
    }

    static String serialize(AuditEntry auditEntry) {
        if (auditEntry == null) {
            throw new IllegalArgumentException("SQL journal payload must not be null");
        }

        ObjectNode payload = OBJECT_MAPPER.createObjectNode();
        payload.put("executionId", auditEntry.getExecutionId());
        payload.put("stageId", auditEntry.getStageId());
        payload.put("changeId", auditEntry.getChangeId());
        payload.put("author", auditEntry.getAuthor());
        putCreatedAt(payload, auditEntry.getCreatedAt());
        payload.put("state", enumName(auditEntry.getState()));
        payload.put("className", auditEntry.getClassName());
        payload.put("methodName", auditEntry.getMethodName());
        payload.put("sourceFile", auditEntry.getSourceFile());
        payload.set("metadata", metadataNode(auditEntry.getMetadata()));
        payload.put("executionMillis", auditEntry.getExecutionMillis());
        payload.put("executionHostname", auditEntry.getExecutionHostname());
        payload.put("errorTrace", auditEntry.getErrorTrace());
        payload.put("type", enumName(auditEntry.getType()));
        payload.put("txType", enumName(auditEntry.getTxType()));
        payload.put("targetSystemId", auditEntry.getTargetSystemId());
        payload.put("order", auditEntry.getOrder());
        payload.put("recoveryStrategy", enumName(auditEntry.getRecoveryStrategy()));
        putNullableBoolean(payload, "transactionFlag", auditEntry.getTransactionFlag());
        payload.put("systemChange", Boolean.TRUE.equals(auditEntry.getSystemChange()));

        try {
            return OBJECT_MAPPER.writeValueAsString(payload);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to serialize SQL journal event payload", exception);
        }
    }

    static AuditEntry deserialize(String serializedPayload) {
        if (serializedPayload == null || serializedPayload.trim().isEmpty()) {
            throw new IllegalStateException("SQL journal event payload must not be null or blank");
        }

        try {
            JsonNode payload = OBJECT_MAPPER.readTree(serializedPayload);
            if (payload == null || !payload.isObject()) {
                throw new IllegalArgumentException("SQL journal event payload must be a JSON object");
            }
            return new AuditEntry(
                    text(payload, "executionId"),
                    text(payload, "stageId"),
                    text(payload, "changeId"),
                    text(payload, "author"),
                    localDateTime(payload, "createdAt"),
                    enumValue(AuditEntry.Status.class, payload, "state"),
                    enumValue(AuditEntry.ChangeType.class, payload, "type"),
                    text(payload, "className"),
                    text(payload, "methodName"),
                    text(payload, "sourceFile"),
                    payloadValue(payload, "executionMillis").asLong(0L),
                    text(payload, "executionHostname"),
                    metadata(payload),
                    payloadValue(payload, "systemChange").asBoolean(false),
                    text(payload, "errorTrace"),
                    AuditTxType.fromString(text(payload, "txType")),
                    text(payload, "targetSystemId"),
                    text(payload, "order"),
                    enumValue(RecoveryStrategy.class, payload, "recoveryStrategy"),
                    nullableBoolean(payload, "transactionFlag"));
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to deserialize SQL journal event payload", exception);
        }
    }

    private static void putCreatedAt(ObjectNode payload, LocalDateTime createdAt) {
        if (createdAt == null) {
            payload.putNull("createdAt");
        } else {
            payload.put("createdAt", createdAt.toString());
        }
    }

    private static void putNullableBoolean(ObjectNode payload, String fieldName, Boolean value) {
        if (value == null) {
            payload.putNull(fieldName);
        } else {
            payload.put(fieldName, value);
        }
    }

    private static JsonNode metadataNode(Object metadata) {
        if (metadata == null) {
            return NullNode.getInstance();
        }
        try {
            return OBJECT_MAPPER.valueToTree(metadata);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Failed to serialize SQL journal event metadata", exception);
        }
    }

    private static Object metadata(JsonNode payload) {
        JsonNode metadata = payload.get("metadata");
        if (metadata == null || metadata.isNull()) {
            return null;
        }
        if (metadata.isTextual()) {
            return metadata.asText();
        }
        return OBJECT_MAPPER.convertValue(metadata, Object.class);
    }

    private static String text(JsonNode payload, String fieldName) {
        JsonNode value = payload.get(fieldName);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static JsonNode payloadValue(JsonNode payload, String fieldName) {
        JsonNode value = payload.get(fieldName);
        return value == null || value.isNull() ? NullNode.getInstance() : value;
    }

    private static LocalDateTime localDateTime(JsonNode payload, String fieldName) {
        String value = text(payload, fieldName);
        return value == null ? null : LocalDateTime.parse(value);
    }

    private static Boolean nullableBoolean(JsonNode payload, String fieldName) {
        JsonNode value = payload.get(fieldName);
        return value == null || value.isNull() ? null : value.asBoolean();
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, JsonNode payload, String fieldName) {
        String value = text(payload, fieldName);
        return value == null ? null : Enum.valueOf(type, value);
    }

    private static String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }
}
