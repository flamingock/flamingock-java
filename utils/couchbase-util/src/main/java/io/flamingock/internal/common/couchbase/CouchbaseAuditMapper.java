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
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.audit.AuditTxType;
import io.flamingock.internal.util.TimeUtil;

import static io.flamingock.internal.util.constants.AuditEntryFieldConstants.KEY_STAGE_ID;
import static io.flamingock.internal.util.constants.AuditEntryFieldConstants.KEY_AUTHOR;
import static io.flamingock.internal.util.constants.AuditEntryFieldConstants.KEY_CHANGE_CLASS;
import static io.flamingock.internal.util.constants.AuditEntryFieldConstants.KEY_INVOKED_METHOD;
import static io.flamingock.internal.util.constants.AuditEntryFieldConstants.KEY_CHANGE_ID;
import static io.flamingock.internal.util.constants.AuditEntryFieldConstants.KEY_ERROR_TRACE;
import static io.flamingock.internal.util.constants.AuditEntryFieldConstants.KEY_EXECUTION_HOSTNAME;
import static io.flamingock.internal.util.constants.AuditEntryFieldConstants.KEY_EXECUTION_ID;
import static io.flamingock.internal.util.constants.AuditEntryFieldConstants.KEY_EXECUTION_MILLIS;
import static io.flamingock.internal.util.constants.AuditEntryFieldConstants.KEY_METADATA;
import static io.flamingock.internal.util.constants.AuditEntryFieldConstants.KEY_ORDER;
import static io.flamingock.internal.util.constants.AuditEntryFieldConstants.KEY_RECOVERY_STRATEGY;
import static io.flamingock.internal.util.constants.AuditEntryFieldConstants.KEY_TRANSACTION_FLAG;
import static io.flamingock.internal.util.constants.AuditEntryFieldConstants.KEY_TX_TYPE;
import static io.flamingock.internal.util.constants.AuditEntryFieldConstants.KEY_STATE;
import static io.flamingock.internal.util.constants.AuditEntryFieldConstants.KEY_SYSTEM_CHANGE;
import static io.flamingock.internal.util.constants.AuditEntryFieldConstants.KEY_TARGET_SYSTEM_ID;
import static io.flamingock.internal.util.constants.AuditEntryFieldConstants.KEY_TIMESTAMP;
import static io.flamingock.internal.util.constants.AuditEntryFieldConstants.KEY_TYPE;

public class CouchbaseAuditMapper {

    public JsonObject toDocument(AuditEntry auditEntry) {
        JsonObject document = JsonObject.create();
        CouchbaseUtils.addFieldToDocument(document, KEY_EXECUTION_ID, auditEntry.getExecutionId());
        CouchbaseUtils.addFieldToDocument(document, KEY_STAGE_ID, auditEntry.getStageId());
        CouchbaseUtils.addFieldToDocument(document, KEY_CHANGE_ID, auditEntry.getTaskId());
        CouchbaseUtils.addFieldToDocument(document, KEY_AUTHOR, auditEntry.getAuthor());
        CouchbaseUtils.addFieldToDocument(document, KEY_TIMESTAMP, TimeUtil.toDate(auditEntry.getCreatedAt()));
        CouchbaseUtils.addFieldToDocument(document, KEY_STATE, auditEntry.getState().name());
        CouchbaseUtils.addFieldToDocument(document, KEY_TYPE, auditEntry.getType().name());
        CouchbaseUtils.addFieldToDocument(document, KEY_CHANGE_CLASS, auditEntry.getClassName());
        CouchbaseUtils.addFieldToDocument(document, KEY_INVOKED_METHOD, auditEntry.getMethodName());
        CouchbaseUtils.addFieldToDocument(document, KEY_METADATA, auditEntry.getMetadata());
        CouchbaseUtils.addFieldToDocument(document, KEY_EXECUTION_MILLIS, auditEntry.getExecutionMillis());
        CouchbaseUtils.addFieldToDocument(document, KEY_EXECUTION_HOSTNAME, auditEntry.getExecutionHostname());
        CouchbaseUtils.addFieldToDocument(document, KEY_ERROR_TRACE, auditEntry.getErrorTrace());
        CouchbaseUtils.addFieldToDocument(document, KEY_SYSTEM_CHANGE, auditEntry.getSystemChange());
        CouchbaseUtils.addFieldToDocument(document, KEY_TX_TYPE, AuditTxType.safeString(auditEntry.getTxType()));
        CouchbaseUtils.addFieldToDocument(document, KEY_TARGET_SYSTEM_ID, auditEntry.getTargetSystemId());
        CouchbaseUtils.addFieldToDocument(document, KEY_ORDER, auditEntry.getOrder());
        CouchbaseUtils.addFieldToDocument(document, KEY_RECOVERY_STRATEGY, auditEntry.getRecoveryStrategy().name());
        CouchbaseUtils.addFieldToDocument(document, KEY_TRANSACTION_FLAG, auditEntry.getTransactionFlag());
        return document;
    }

    public AuditEntry fromDocument(JsonObject jsonObject) {
        // Parse OperationType with null safety for backward compatibility
        AuditTxType txType = null;
        if (jsonObject.get(KEY_TX_TYPE) != null && jsonObject.getString(KEY_TX_TYPE) != null) {
            try {
                txType = AuditTxType.fromString(jsonObject.getString(KEY_TX_TYPE));
            } catch (IllegalArgumentException e) {
                // Handle case where stored value is invalid - default to null
                txType = AuditTxType.NON_TX;
            }
        }

        return new AuditEntry(jsonObject.getString(KEY_EXECUTION_ID),
                jsonObject.getString(KEY_STAGE_ID),
                jsonObject.getString(KEY_CHANGE_ID),
                jsonObject.getString(KEY_AUTHOR),
                jsonObject.get(KEY_TIMESTAMP) != null ? TimeUtil.toLocalDateTime(jsonObject.getLong(KEY_TIMESTAMP)) : null,
                jsonObject.get(KEY_STATE) != null ? AuditEntry.Status.valueOf(jsonObject.getString(KEY_STATE)) : null,
                jsonObject.get(KEY_TYPE) != null ? AuditEntry.ExecutionType.valueOf(jsonObject.getString(KEY_TYPE)) : null,
                jsonObject.getString(KEY_CHANGE_CLASS),
                jsonObject.getString(KEY_INVOKED_METHOD),
                jsonObject.getLong(KEY_EXECUTION_MILLIS),
                jsonObject.getString(KEY_EXECUTION_HOSTNAME),
                jsonObject.get(KEY_METADATA) != null ? jsonObject.getObject(KEY_METADATA).toMap() : null,
                jsonObject.getBoolean(KEY_SYSTEM_CHANGE),
                jsonObject.getString(KEY_ERROR_TRACE),
                txType,
                jsonObject.getString(KEY_TARGET_SYSTEM_ID),
                jsonObject.getString(KEY_ORDER),
                jsonObject.getString(KEY_RECOVERY_STRATEGY) != null
                        ? RecoveryStrategy.valueOf(jsonObject.getString(KEY_RECOVERY_STRATEGY))
                        : RecoveryStrategy.MANUAL_INTERVENTION,
                jsonObject.getBoolean(KEY_TRANSACTION_FLAG)
        );
    }
}
