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
package io.flamingock.internal.core.store.audit.community;

import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.audit.AuditReader;
import io.flamingock.internal.common.core.audit.AuditSnapshotBuilder;

import java.util.Map;

public interface CommunityAuditReader extends AuditReader {
    default Map<String, AuditEntry> getAuditSnapshotByChangeId() {
        AuditSnapshotBuilder builder = new AuditSnapshotBuilder();
        getAuditHistory().forEach(builder::addEntry);
        return builder.buildMap();
    }
}
