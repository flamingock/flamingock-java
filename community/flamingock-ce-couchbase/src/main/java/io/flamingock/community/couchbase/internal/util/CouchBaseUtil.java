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

package io.flamingock.community.couchbase.internal.util;

import com.couchbase.client.java.json.JsonObject;
import io.flamingock.internal.core.engine.lock.LockAcquisition;
import io.flamingock.internal.core.community.lock.LockEntry;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.core.engine.lock.LockStatus;
import io.flamingock.internal.util.id.RunnerId;
import io.flamingock.internal.util.TimeUtil;

import java.time.LocalDateTime;
import java.time.ZoneId;

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
import static io.flamingock.internal.core.community.lock.LockEntryField.EXPIRES_AT_FIELD;
import static io.flamingock.internal.core.community.lock.LockEntryField.KEY_FIELD;
import static io.flamingock.internal.core.community.lock.LockEntryField.OWNER_FIELD;
import static io.flamingock.internal.core.community.lock.LockEntryField.STATUS_FIELD;

public final class CouchBaseUtil {
    private CouchBaseUtil() {
    }

    public static AuditEntry auditEntryFromEntity(JsonObject jsonObject) {
        return new AuditEntry(jsonObject.getString(KEY_EXECUTION_ID),
                null,//TODO: add stage name
                jsonObject.getString(KEY_CHANGE_ID),
                jsonObject.getString(KEY_AUTHOR),
                jsonObject.get(KEY_TIMESTAMP) != null ? TimeUtil.toLocalDateTime(jsonObject.getLong(KEY_TIMESTAMP)) : null,
                jsonObject.get(KEY_STATE) != null ? AuditEntry.Status.valueOf(jsonObject.getString(KEY_STATE)) : null,
                jsonObject.get(KEY_TYPE) != null ? AuditEntry.ExecutionType.valueOf(jsonObject.getString(KEY_TYPE)) : null,
                jsonObject.getString(KEY_CHANGEUNIT_CLASS),
                jsonObject.getString(KEY_INVOKED_METHOD),
                jsonObject.getLong(KEY_EXECUTION_MILLIS),
                jsonObject.getString(KEY_EXECUTION_HOSTNAME),
                jsonObject.get(KEY_METADATA) != null ? jsonObject.getObject(KEY_METADATA).toMap() : null,
                jsonObject.getBoolean(KEY_SYSTEM_CHANGE),
                jsonObject.getString(KEY_ERROR_TRACE));
    }

    public static LockEntry lockEntryFromEntity(JsonObject jsonObject) {
        return new LockEntry(jsonObject.getString(KEY_FIELD),
                jsonObject.containsKey(STATUS_FIELD) ? LockStatus.valueOf(jsonObject.getString(STATUS_FIELD)) : null,
                jsonObject.getString(OWNER_FIELD),
                TimeUtil.toLocalDateTime(jsonObject.getLong(EXPIRES_AT_FIELD)));
    }

    public static LockAcquisition lockAcquisitionFromEntity(JsonObject jsonObject) {

        long expiration = TimeUtil.toLocalDateTime(jsonObject.getLong(EXPIRES_AT_FIELD)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long now = LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long diffMillis = expiration - now;
        return new LockAcquisition(RunnerId.fromString(jsonObject.getString(OWNER_FIELD)), diffMillis);
    }
}
