/*
 * Copyright 2025 Flamingock (https://oss.flamingock.io)
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
 
package io.flamingock.core;

import io.flamingock.internal.common.cloud.audit.AuditEntryRequest;
import io.flamingock.internal.common.core.audit.AuditEntry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class ConversionsTest {


    @Test
    @DisplayName("AuditEntryRequest.ExecutionType should match AuditEntry.ExecutionType")
    void auditEntryExecutionTypeShouldAuditEntryRequestExecutionType() {
        Set<String> auditEntryRequestExecutionTypeValues = Arrays
                .stream(AuditEntryRequest.ExecutionType.values())
                .map(AuditEntryRequest.ExecutionType::name)
                .collect(Collectors.toSet());
        List<String> auditEntryExecutionTypeValues = Arrays.stream(AuditEntry.ExecutionType.values())
                .map(AuditEntry.ExecutionType::name)
                .collect(Collectors.toList());

        if (auditEntryExecutionTypeValues.size() != auditEntryRequestExecutionTypeValues.size()) {
            throw new IllegalArgumentException("Enums AuditEntryRequest.ExecutionType and AuditEntry.ExecutionType should match");
        }

        if(auditEntryExecutionTypeValues.stream().anyMatch(value -> !auditEntryRequestExecutionTypeValues.contains(value))) {
            throw new IllegalArgumentException("Enums AuditEntryRequest.ExecutionType and AuditEntry.ExecutionType should match");
        }
    }


    @Test
    @DisplayName("AuditEntryRequest.Status should match AuditEntry.Status")
    void auditEntryStateShouldAuditEntryRequestState() {
        Set<String> auditEntryRequestExecutionTypeValues = Arrays
                .stream(AuditEntryRequest.Status.values())
                .map(AuditEntryRequest.Status::name)
                .collect(Collectors.toSet());
        List<String> auditEntryExecutionTypeValues = Arrays.stream(AuditEntry.Status.values())
                .map(AuditEntry.Status::name)
                .collect(Collectors.toList());

        if (auditEntryExecutionTypeValues.size() != auditEntryRequestExecutionTypeValues.size()) {
            throw new IllegalArgumentException("Enums AuditEntryRequest.Status and AuditEntry.Status should match");
        }

        if(auditEntryExecutionTypeValues.stream().anyMatch(value -> !auditEntryRequestExecutionTypeValues.contains(value))) {
            throw new IllegalArgumentException("Enums AuditEntryRequest.Status and AuditEntry.Status should match");
        }
    }
}
