/*
 * Copyright 2026 Flamingock (https://www.flamingock.io)
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
package io.flamingock.internal.core.operation.audit;

import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.response.data.AuditListResponseData;
import io.flamingock.internal.common.core.response.data.AuditListResponseData.AuditEntryDto;
import io.flamingock.internal.core.operation.AbstractOperationResult;

import java.util.List;
import java.util.stream.Collectors;

public class AuditListResult extends AbstractOperationResult {
    private final List<AuditEntry> auditEntries;
    private final boolean extended;

    public AuditListResult(List<AuditEntry> auditEntries) {
        this(auditEntries, false);
    }

    public AuditListResult(List<AuditEntry> auditEntries, boolean extended) {
        this.auditEntries = auditEntries;
        this.extended = extended;
    }

    public List<AuditEntry> getAuditEntries() {
        return auditEntries;
    }

    public boolean isExtended() {
        return extended;
    }

    @Override
    public Object toResponseData() {
        List<AuditEntryDto> dtos = auditEntries.stream()
                .map(this::toDto)
                .collect(Collectors.toList());
        return new AuditListResponseData(dtos);
    }

    private AuditEntryDto toDto(AuditEntry entry) {
        if (extended) {
            return new AuditEntryDto(
                    entry.getTaskId(),
                    entry.getAuthor(),
                    entry.getState() != null ? entry.getState().name() : null,
                    entry.getStageId(),
                    entry.getCreatedAt(),
                    entry.getExecutionMillis(),
                    entry.getExecutionId(),
                    entry.getClassName(),
                    entry.getMethodName(),
                    entry.getExecutionHostname(),
                    entry.getTargetSystemId()
            );
        } else {
            return new AuditEntryDto(
                    entry.getTaskId(),
                    entry.getAuthor(),
                    entry.getState() != null ? entry.getState().name() : null,
                    entry.getStageId(),
                    entry.getCreatedAt(),
                    entry.getExecutionMillis()
            );
        }
    }
}
