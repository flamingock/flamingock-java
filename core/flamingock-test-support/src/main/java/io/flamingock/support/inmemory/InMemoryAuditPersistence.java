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
package io.flamingock.support.inmemory;

import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.core.store.audit.community.CommunityAuditPersistence;
import io.flamingock.internal.util.Result;

import java.util.List;

public class InMemoryAuditPersistence implements CommunityAuditPersistence {
    
    private final InMemoryAuditWriter auditWriter;
    private final InMemoyAuditReader auditReader;
    
    public InMemoryAuditPersistence(InMemoryAuditStorage auditStorage) {
        this.auditWriter = new InMemoryAuditWriter(auditStorage);
        this.auditReader = new InMemoyAuditReader(auditStorage);
    }

    @Override
    public List<AuditEntry> getAuditHistory() {
        return auditReader.getAuditHistory();
    }

    @Override
    public Result writeEntry(AuditEntry auditEntry) {
        return auditWriter.writeEntry(auditEntry);
    }
}