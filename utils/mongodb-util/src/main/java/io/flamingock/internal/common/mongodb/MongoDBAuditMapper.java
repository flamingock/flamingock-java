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
package io.flamingock.internal.common.mongodb;

import io.flamingock.api.RecoveryStrategy;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.audit.AuditTxType;
import io.flamingock.internal.util.TimeUtil;

import java.util.function.Supplier;

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
import static io.flamingock.internal.util.constants.AuditEntryFieldConstants.KEY_STAGE_ID;
import static io.flamingock.internal.util.constants.AuditEntryFieldConstants.KEY_STATE;
import static io.flamingock.internal.util.constants.AuditEntryFieldConstants.KEY_SYSTEM_CHANGE;
import static io.flamingock.internal.util.constants.AuditEntryFieldConstants.KEY_TARGET_SYSTEM_ID;
import static io.flamingock.internal.util.constants.AuditEntryFieldConstants.KEY_TIMESTAMP;
import static io.flamingock.internal.util.constants.AuditEntryFieldConstants.KEY_TYPE;

public class MongoDBAuditMapper<DOCUMENT_WRAPPER extends DocumentHelper> {

    private final Supplier<DOCUMENT_WRAPPER> documentckSupplier;

    public MongoDBAuditMapper(Supplier<DOCUMENT_WRAPPER> documentCreator) {
        this.documentckSupplier = documentCreator;
    }

    public DOCUMENT_WRAPPER toDocument(AuditEntry auditEntry) {
        DOCUMENT_WRAPPER document = documentckSupplier.get();
        document.append(KEY_EXECUTION_ID, auditEntry.getExecutionId());
        document.append(KEY_STAGE_ID, auditEntry.getStageId());
        document.append(KEY_CHANGE_ID, auditEntry.getTaskId());
        document.append(KEY_AUTHOR, auditEntry.getAuthor());
        document.append(KEY_TIMESTAMP, TimeUtil.toDate(auditEntry.getCreatedAt()));
        document.append(KEY_STATE, auditEntry.getState().name());
        document.append(KEY_TYPE, auditEntry.getType().name());
        document.append(KEY_CHANGE_CLASS, auditEntry.getClassName());
        document.append(KEY_INVOKED_METHOD, auditEntry.getMethodName());
        document.append(KEY_METADATA, auditEntry.getMetadata());
        document.append(KEY_EXECUTION_MILLIS, auditEntry.getExecutionMillis());
        document.append(KEY_EXECUTION_HOSTNAME, auditEntry.getExecutionHostname());
        document.append(KEY_ERROR_TRACE, auditEntry.getErrorTrace());
        document.append(KEY_SYSTEM_CHANGE, auditEntry.getSystemChange());
        document.append(KEY_TX_TYPE, AuditTxType.safeString(auditEntry.getTxType()));
        document.append(KEY_TARGET_SYSTEM_ID, auditEntry.getTargetSystemId());
        document.append(KEY_ORDER, auditEntry.getOrder());
        document.append(KEY_RECOVERY_STRATEGY, auditEntry.getRecoveryStrategy().name());
        document.append(KEY_TRANSACTION_FLAG, auditEntry.getTransactionFlag());
        return document;
    }

    public AuditEntry fromDocument(DocumentHelper entry) {
        // Parse OperationType with null safety for backward compatibility
        AuditTxType txType = null;
        if (entry.containsKey(KEY_TX_TYPE) && entry.getString(KEY_TX_TYPE) != null) {
            try {
                txType = AuditTxType.fromString(entry.getString(KEY_TX_TYPE));
            } catch (IllegalArgumentException e) {
                // Handle case where stored value is invalid - default to null
                txType = AuditTxType.NON_TX;
            }
        }
        
        return new AuditEntry(
                entry.getString(KEY_EXECUTION_ID),
                entry.getString(KEY_STAGE_ID),
                entry.getString(KEY_CHANGE_ID),
                entry.getString(KEY_AUTHOR),
                TimeUtil.toLocalDateTime(entry.get(KEY_TIMESTAMP)),
                entry.containsKey(KEY_STATE) ? AuditEntry.Status.valueOf(entry.getString(KEY_STATE)) : null,
                entry.containsKey(KEY_TYPE) ? AuditEntry.ExecutionType.valueOf(entry.getString(KEY_TYPE)) : null,
                entry.getString(KEY_CHANGE_CLASS),
                entry.getString(KEY_INVOKED_METHOD),
                entry.containsKey(KEY_EXECUTION_MILLIS) && entry.get(KEY_EXECUTION_MILLIS) != null
                        ? ((Number) entry.get(KEY_EXECUTION_MILLIS)).longValue() : -1L,
                entry.getString(KEY_EXECUTION_HOSTNAME),
                entry.get(KEY_METADATA),
                entry.getBoolean(KEY_SYSTEM_CHANGE) != null && entry.getBoolean(KEY_SYSTEM_CHANGE),
                entry.getString(KEY_ERROR_TRACE),
                txType,
                entry.getString(KEY_TARGET_SYSTEM_ID),
                entry.getString(KEY_ORDER),
                entry.getString(KEY_RECOVERY_STRATEGY) != null
                        ? RecoveryStrategy.valueOf(entry.getString(KEY_RECOVERY_STRATEGY))
                        : RecoveryStrategy.MANUAL_INTERVENTION,
                entry.getBoolean(KEY_TRANSACTION_FLAG)

        );

    }
}
