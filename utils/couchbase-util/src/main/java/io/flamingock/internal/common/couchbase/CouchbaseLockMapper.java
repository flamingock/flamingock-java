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
import io.flamingock.internal.core.community.lock.LockEntry;
import io.flamingock.internal.core.community.lock.LockEntryField;
import io.flamingock.internal.core.engine.lock.LockAcquisition;
import io.flamingock.internal.core.engine.lock.LockStatus;
import io.flamingock.internal.util.TimeUtil;
import io.flamingock.internal.util.id.RunnerId;

import java.time.LocalDateTime;
import java.time.ZoneId;

import static io.flamingock.internal.core.community.lock.LockEntryField.KEY_FIELD;
import static io.flamingock.internal.core.community.lock.LockEntryField.OWNER_FIELD;
import static io.flamingock.internal.core.community.lock.LockEntryField.STATUS_FIELD;
import static io.flamingock.internal.core.community.lock.LockEntryField.EXPIRES_AT_FIELD;

public class CouchbaseLockMapper {

    public JsonObject toDocument(LockEntry lockEntry) {
        JsonObject document = JsonObject.create();
        CouchbaseUtils.addFieldToDocument(document, KEY_FIELD, lockEntry.getKey());
        CouchbaseUtils.addFieldToDocument(document, OWNER_FIELD, lockEntry.getOwner());
        CouchbaseUtils.addFieldToDocument(document, STATUS_FIELD, lockEntry.getStatus().name());
        CouchbaseUtils.addFieldToDocument(document, EXPIRES_AT_FIELD, TimeUtil.toDate(lockEntry.getExpiresAt()));
        return document;
    }

    public LockEntry lockEntryFromDocument(JsonObject jsonObject) {
        return new LockEntry(jsonObject.getString(LockEntryField.KEY_FIELD),
                jsonObject.containsKey(LockEntryField.STATUS_FIELD) ? LockStatus.valueOf(jsonObject.getString(LockEntryField.STATUS_FIELD)) : null,
                jsonObject.getString(LockEntryField.OWNER_FIELD),
                TimeUtil.toLocalDateTime(jsonObject.getLong(LockEntryField.EXPIRES_AT_FIELD)));
    }

    public LockAcquisition lockAcquisitionFromDocument(JsonObject jsonObject) {
        long expiration = TimeUtil.toLocalDateTime(jsonObject.getLong(LockEntryField.EXPIRES_AT_FIELD)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long now = LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long diffMillis = expiration - now;
        return new LockAcquisition(RunnerId.fromString(jsonObject.getString(LockEntryField.OWNER_FIELD)), diffMillis);
    }
}
