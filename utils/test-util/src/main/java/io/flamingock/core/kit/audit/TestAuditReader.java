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
package io.flamingock.core.kit.audit;

import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.audit.AuditReader;
import io.flamingock.internal.core.engine.audit.domain.AuditSnapshotMapBuilder;


import java.util.List;
import java.util.Map;

public class TestAuditReader implements AuditReader {
    
    private final AuditStorage auditStorage;
    
    public TestAuditReader(AuditStorage auditStorage) {
        this.auditStorage = auditStorage;
    }


    @Override
    public Map<String, AuditEntry> getAuditSnapshotByChangeId() {
        List<AuditEntry> allAuditEntries = auditStorage.getAuditEntries();

        // Build AuditStageStatus from all audit entries
        AuditSnapshotMapBuilder builder = new AuditSnapshotMapBuilder();

        for (AuditEntry entry : allAuditEntries) {
            builder.addEntry(entry);
        }

        return builder.build();
    }

}