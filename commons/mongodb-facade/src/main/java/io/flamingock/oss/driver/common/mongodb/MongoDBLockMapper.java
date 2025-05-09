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

package io.flamingock.oss.driver.common.mongodb;

import io.flamingock.core.engine.lock.LockAcquisition;
import io.flamingock.core.community.lock.LockEntry;
import io.flamingock.commons.utils.RunnerId;
import io.flamingock.commons.utils.TimeUtil;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.function.Supplier;

import static io.flamingock.core.community.lock.LockEntryField.EXPIRES_AT_FIELD;
import static io.flamingock.core.community.lock.LockEntryField.KEY_FIELD;
import static io.flamingock.core.community.lock.LockEntryField.OWNER_FIELD;
import static io.flamingock.core.community.lock.LockEntryField.STATUS_FIELD;

public class MongoDBLockMapper<DOCUMENT_WRAPPER extends DocumentWrapper> {

    private final Supplier<DOCUMENT_WRAPPER> documentckSupplier;

    public MongoDBLockMapper(Supplier<DOCUMENT_WRAPPER> documentCreator) {
        this.documentckSupplier = documentCreator;
    }

    public DOCUMENT_WRAPPER toDocument(LockEntry lockEntry) {
        DOCUMENT_WRAPPER document = documentckSupplier.get();
        document.append(KEY_FIELD, lockEntry.getKey());
        document.append(OWNER_FIELD, lockEntry.getOwner());
        document.append(STATUS_FIELD, lockEntry.getStatus().name());
        document.append(EXPIRES_AT_FIELD, TimeUtil.toDate(lockEntry.getExpiresAt()));
        return document;
    }

    public LockAcquisition fromDocument(DocumentWrapper document) {
        long expiration = TimeUtil.toLocalDateTime(document.get(EXPIRES_AT_FIELD)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long now = LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long diffMillis = expiration - now;
        return new LockAcquisition(RunnerId.fromString(document.getString(OWNER_FIELD)), diffMillis);
//        return new LockEntry(
//                document.getString(KEY_FIELD),
//                document.containsKey(STATUS_FIELD) ? LockStatus.valueOf(document.getString(STATUS_FIELD)) : null,
//                document.getString(OWNER_FIELD),
//                TimeUtil.toLocalDateTime(document.get(EXPIRES_AT_FIELD)));

    }
}
