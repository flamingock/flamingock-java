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
package io.flamingock.support.precondition;

import io.flamingock.internal.common.core.audit.AuditWriter;
import io.flamingock.support.domain.AuditEntryDefinition;

import java.util.List;

/**
 * Inserts audit entry preconditions into the audit store before test execution.
 *
 * <p>This class is responsible for converting {@link AuditEntryDefinition} instances
 * to actual audit entries and inserting them into the audit store. It follows the
 * dependency injection pattern by receiving the {@link AuditWriter} via constructor.</p>
 */
public class PreconditionInserter {

    private final AuditWriter auditWriter;

    /**
     * Creates a new precondition inserter with the given audit writer.
     *
     * @param auditWriter the audit writer to use for inserting entries
     */
    public PreconditionInserter(AuditWriter auditWriter) {
        this.auditWriter = auditWriter;
    }

    /**
     * Inserts the given preconditions into the audit store.
     *
     * <p>Each {@link AuditEntryDefinition} is converted to an {@code AuditEntry}
     * and written to the audit store. If the preconditions list is null or empty,
     * this method does nothing.</p>
     *
     * @param preconditions the list of audit entry definitions to insert
     */
    public void insert(List<AuditEntryDefinition> preconditions) {
        if (preconditions == null || preconditions.isEmpty()) {
            return;
        }
        for (AuditEntryDefinition definition : preconditions) {
            auditWriter.writeEntry(definition.toAuditEntry());
        }
    }
}
