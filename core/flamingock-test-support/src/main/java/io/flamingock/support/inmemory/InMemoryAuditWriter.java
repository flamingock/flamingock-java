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
import io.flamingock.internal.core.store.audit.LifecycleAuditWriter;
import io.flamingock.internal.core.store.audit.domain.ExecutionAuditContextBundle;
import io.flamingock.internal.core.store.audit.domain.RollbackAuditContextBundle;
import io.flamingock.internal.core.store.audit.domain.StartExecutionAuditContextBundle;
import io.flamingock.internal.util.Result;

class InMemoryAuditWriter implements LifecycleAuditWriter {

    private final InMemoryAuditStorage auditStorage;

    public InMemoryAuditWriter(InMemoryAuditStorage auditStorage) {
        this.auditStorage = auditStorage;
    }
    
    @Override
    public Result writeStartExecution(StartExecutionAuditContextBundle auditContextBundle) {
        try {
            AuditEntry auditEntry = auditContextBundle.toAuditEntry();
            return writeEntry(auditEntry);
        } catch (Exception e) {
            return new Result.Error(e);
        }
    }
    
    @Override
    public Result writeExecution(ExecutionAuditContextBundle auditContextBundle) {
        try {
            AuditEntry auditEntry = auditContextBundle.toAuditEntry();
            return writeEntry(auditEntry);
        } catch (Exception e) {
            return new Result.Error(e);
        }
    }
    
    @Override
    public Result writeRollback(RollbackAuditContextBundle auditContextBundle) {
        try {
            AuditEntry auditEntry = auditContextBundle.toAuditEntry();
            return writeEntry(auditEntry);
        } catch (Exception e) {
            return new Result.Error(e);
        }
    }
    
    /**
     * Write an audit entry to the storage
     */
    public Result writeEntry(AuditEntry auditEntry) {
        try {
            auditStorage.addAuditEntry(auditEntry);
            return Result.OK();
        } catch (Exception e) {
            return new Result.Error(e);
        }
    }
}