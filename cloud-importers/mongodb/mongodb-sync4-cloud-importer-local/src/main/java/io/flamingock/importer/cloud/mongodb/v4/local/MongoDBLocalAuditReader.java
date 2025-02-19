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

package io.flamingock.importer.cloud.mongodb.v4.local;

import com.mongodb.client.MongoCollection;
import io.flamingock.commons.utils.TimeUtil;
import io.flamingock.core.engine.audit.writer.AuditEntry;
import io.flamingock.importer.cloud.common.AuditReader;
import org.bson.Document;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static io.flamingock.core.local.AuditEntryField.*;

public class MongoDBLocalAuditReader implements AuditReader {

    private final MongoCollection<Document> changeUnitsStorage;

    public MongoDBLocalAuditReader(MongoCollection<Document> changeUnitsStorage) {
        this.changeUnitsStorage = changeUnitsStorage;
    }

    static AuditEntry toAuditEntry(Document document) {
        return new AuditEntry(
                document.getString(KEY_EXECUTION_ID),
                null,//TODO: add stage name
                document.getString(KEY_CHANGE_ID),
                document.getString(KEY_AUTHOR),
                TimeUtil.toLocalDateTime(document.get(KEY_TIMESTAMP)),
                document.containsKey(KEY_STATE) ? AuditEntry.Status.valueOf(document.getString(KEY_STATE)) : null,
                document.containsKey(KEY_TYPE) ? AuditEntry.ExecutionType.valueOf(document.getString(KEY_TYPE)) : null,
                document.getString(KEY_CHANGELOG_CLASS),
                document.getString(KEY_CHANGESET_METHOD),
                document.containsKey(KEY_EXECUTION_MILLIS) && document.get(KEY_EXECUTION_MILLIS) != null
                        ? ((Number) document.get(KEY_EXECUTION_MILLIS)).longValue() : -1L,
                document.getString(KEY_EXECUTION_HOSTNAME),
                document.get(KEY_METADATA),
                document.getBoolean(KEY_SYSTEM_CHANGE) != null && document.getBoolean(KEY_SYSTEM_CHANGE),
                document.getString(KEY_ERROR_TRACE)
        );
    }

    @Override
    public List<AuditEntry> readAuditEntries() {
        return changeUnitsStorage
                .find()
                .into(new ArrayList<>())
                .stream()
                .map(MongoDBLocalAuditReader::toAuditEntry)
                .sorted(Comparator.comparing(AuditEntry::getCreatedAt))
                .collect(Collectors.toList());
    }
}
