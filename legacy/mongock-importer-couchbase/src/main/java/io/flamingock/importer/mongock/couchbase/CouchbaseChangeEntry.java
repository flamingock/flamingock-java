/*
 * Copyright 2023 Flamingock (https://www.flamingock.io)
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
package io.flamingock.importer.mongock.couchbase;

import com.couchbase.client.java.json.JsonObject;
import io.flamingock.api.RecoveryStrategy;
import io.flamingock.internal.common.core.audit.AuditEntry;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

public class CouchbaseChangeEntry {
    private String executionId;
    private String changeId;
    private String author;
    private Long timestamp;
    private String state;
    private String type;
    private String changeLogClass;
    private String changeSetMethod;
    private String metadata;
    private Long executionMillis;
    private String executionHostName;
    private String errorTrace;
    private Boolean systemChange;

    public String getExecutionId() {
        return executionId;
    }

    public void setExecutionId(String executionId) {
        this.executionId = executionId;
    }

    public static CouchbaseChangeEntry fromJson(JsonObject doc) {
        CouchbaseChangeEntry entry = new CouchbaseChangeEntry();
        entry.executionId = doc.getString("executionId");
        entry.changeId = doc.getString("changeId");
        entry.author = doc.getString("author");
        entry.timestamp = doc.getLong("timestamp");
        entry.state = doc.getString("state");
        entry.type = doc.getString("type");
        entry.changeLogClass = doc.getString("changeLogClass");
        entry.changeSetMethod = doc.getString("changeSetMethod");
        entry.metadata = doc.getString("metadata");
        entry.executionMillis = parseLong(doc.get("executionMillis"));
        entry.executionHostName = doc.getString("executionHostName");
        entry.errorTrace = doc.getString("errorTrace");
        entry.systemChange = doc.getBoolean("systemChange");
        return entry;
    }

    private static Long parseLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number) return ((Number) value).longValue();
        if (value instanceof String) return Long.parseLong((String) value);
        throw new IllegalArgumentException("Cannot convert value to Long: " + value);
    }

    public AuditEntry toAuditEntry() {
        LocalDateTime ts = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault());

        MongockChangeState stateEnum = MongockChangeState.valueOf(state);
        MongockChangeType changeType = MongockChangeType.valueOf(type);

        return new AuditEntry(
                executionId,
                null,
                changeId,
                author,
                ts,
                stateEnum.toAuditStatus(),
                changeType.toAuditType(),
                changeLogClass,
                changeSetMethod,
                null, //TODO: set sourceFile
                executionMillis,
                executionHostName,
                metadata,
                systemChange != null && systemChange,
                errorTrace,
                null,
                null,
                null,
                RecoveryStrategy.MANUAL_INTERVENTION,
                null
        );
    }
}