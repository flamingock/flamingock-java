/*
 * Copyright 2023 Flamingock (https://oss.flamingock.io)
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
import io.flamingock.internal.util.TimeUtil;

import java.util.function.Supplier;

import static io.flamingock.internal.core.community.Constants.KEY_AUTHOR;
import static io.flamingock.internal.core.community.Constants.KEY_CHANGEUNIT_CLASS;
import static io.flamingock.internal.core.community.Constants.KEY_INVOKED_METHOD;
import static io.flamingock.internal.core.community.Constants.KEY_CHANGE_ID;
import static io.flamingock.internal.core.community.Constants.KEY_ERROR_TRACE;
import static io.flamingock.internal.core.community.Constants.KEY_EXECUTION_HOSTNAME;
import static io.flamingock.internal.core.community.Constants.KEY_EXECUTION_ID;
import static io.flamingock.internal.core.community.Constants.KEY_EXECUTION_MILLIS;
import static io.flamingock.internal.core.community.Constants.KEY_METADATA;
import static io.flamingock.internal.core.community.Constants.KEY_STATE;
import static io.flamingock.internal.core.community.Constants.KEY_SYSTEM_CHANGE;
import static io.flamingock.internal.core.community.Constants.KEY_TIMESTAMP;
import static io.flamingock.internal.core.community.Constants.KEY_TYPE;

public class MongoDBAuditMapper<DOCUMENT_WRAPPER extends DocumentWrapper> {

    private final Supplier<DOCUMENT_WRAPPER> documentckSupplier;

    public MongoDBAuditMapper(Supplier<DOCUMENT_WRAPPER> documentCreator) {
        this.documentckSupplier = documentCreator;
    }

    public DOCUMENT_WRAPPER toDocument(AuditEntry auditEntry) {
        DOCUMENT_WRAPPER document = documentckSupplier.get();
        document.append(KEY_EXECUTION_ID, auditEntry.getExecutionId());
        document.append(KEY_CHANGE_ID, auditEntry.getTaskId());
        document.append(KEY_AUTHOR, auditEntry.getAuthor());
        document.append(KEY_TIMESTAMP, TimeUtil.toDate(auditEntry.getCreatedAt()));
        document.append(KEY_STATE, auditEntry.getState().name());
        document.append(KEY_TYPE, auditEntry.getType().name());
        document.append(KEY_CHANGEUNIT_CLASS, auditEntry.getClassName());
        document.append(KEY_INVOKED_METHOD, auditEntry.getMethodName());
        document.append(KEY_METADATA, auditEntry.getMetadata());
        document.append(KEY_EXECUTION_MILLIS, auditEntry.getExecutionMillis());
        document.append(KEY_EXECUTION_HOSTNAME, auditEntry.getExecutionHostname());
        document.append(KEY_ERROR_TRACE, auditEntry.getErrorTrace());
        document.append(KEY_SYSTEM_CHANGE, auditEntry.getSystemChange());
        return document;
    }

    public AuditEntry fromDocument(DocumentWrapper entry) {
        return new AuditEntry(
                entry.getString(KEY_EXECUTION_ID),
                null,//TODO: add stage name
                entry.getString(KEY_CHANGE_ID),
                entry.getString(KEY_AUTHOR),
                TimeUtil.toLocalDateTime(entry.get(KEY_TIMESTAMP)),
                entry.containsKey(KEY_STATE) ? AuditEntry.Status.valueOf(entry.getString(KEY_STATE)) : null,
                entry.containsKey(KEY_TYPE) ? AuditEntry.ExecutionType.valueOf(entry.getString(KEY_TYPE)) : null,
                entry.getString(KEY_CHANGEUNIT_CLASS),
                entry.getString(KEY_INVOKED_METHOD),
                entry.containsKey(KEY_EXECUTION_MILLIS) && entry.get(KEY_EXECUTION_MILLIS) != null
                        ? ((Number) entry.get(KEY_EXECUTION_MILLIS)).longValue() : -1L,
                entry.getString(KEY_EXECUTION_HOSTNAME),
                entry.get(KEY_METADATA),
                entry.getBoolean(KEY_SYSTEM_CHANGE) != null && entry.getBoolean(KEY_SYSTEM_CHANGE),
                entry.getString(KEY_ERROR_TRACE));

    }
}
