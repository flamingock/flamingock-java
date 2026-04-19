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
package io.flamingock.internal.core.external.store.audit.domain;

import io.flamingock.internal.common.core.targets.TargetSystemAuditMarkType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

class OperationMappingTest {

    @Test
    @DisplayName("EXECUTION should map to APPLIED")
    void executionMapsToApplied() {
        assertEquals(TargetSystemAuditMarkType.APPLIED,
                AuditContextBundle.Operation.EXECUTION.toOngoingStatusOperation());
    }

    @Test
    @DisplayName("ROLLBACK should map to ROLLED_BACK")
    void rollbackMapsToRolledBack() {
        assertEquals(TargetSystemAuditMarkType.ROLLED_BACK,
                AuditContextBundle.Operation.ROLLBACK.toOngoingStatusOperation());
    }

    @Test
    @DisplayName("APPLIED should map back to EXECUTION")
    void appliedMapsToExecution() {
        assertEquals(AuditContextBundle.Operation.EXECUTION,
                AuditContextBundle.Operation.fromOngoingStatusOperation(TargetSystemAuditMarkType.APPLIED));
    }

    @Test
    @DisplayName("ROLLED_BACK should map back to ROLLBACK")
    void rolledBackMapsToRollback() {
        assertEquals(AuditContextBundle.Operation.ROLLBACK,
                AuditContextBundle.Operation.fromOngoingStatusOperation(TargetSystemAuditMarkType.ROLLED_BACK));
    }

    @Test
    @DisplayName("START_EXECUTION should throw when mapped to TargetSystemAuditMarkType")
    void startExecutionHasNoMapping() {
        assertThrows(IllegalArgumentException.class,
                () -> AuditContextBundle.Operation.START_EXECUTION.toOngoingStatusOperation());
    }

    @Test
    @DisplayName("NONE should throw when mapped to Operation")
    void noneHasNoMapping() {
        assertThrows(IllegalArgumentException.class,
                () -> AuditContextBundle.Operation.fromOngoingStatusOperation(TargetSystemAuditMarkType.NONE));
    }

    @ParameterizedTest(name = "Every mappable Operation.{0} should round-trip through TargetSystemAuditMarkType")
    @EnumSource(value = AuditContextBundle.Operation.class, names = {"EXECUTION", "ROLLBACK"})
    void operationRoundTrips(AuditContextBundle.Operation operation) {
        TargetSystemAuditMarkType markType = operation.toOngoingStatusOperation();
        AuditContextBundle.Operation back = AuditContextBundle.Operation.fromOngoingStatusOperation(markType);
        assertEquals(operation, back);
    }

    @ParameterizedTest(name = "Every mappable TargetSystemAuditMarkType.{0} should round-trip through Operation")
    @EnumSource(value = TargetSystemAuditMarkType.class, names = {"APPLIED", "ROLLED_BACK"})
    void markTypeRoundTrips(TargetSystemAuditMarkType markType) {
        AuditContextBundle.Operation operation = AuditContextBundle.Operation.fromOngoingStatusOperation(markType);
        TargetSystemAuditMarkType back = operation.toOngoingStatusOperation();
        assertEquals(markType, back);
    }

    @Test
    @DisplayName("All mappable Operations must have a mapping — fails if a new Operation is added without updating the map")
    void allMappableOperationsCovered() {
        int expectedMappable = 2; // EXECUTION, ROLLBACK — START_EXECUTION is intentionally unmapped
        int actualMappable = 0;
        for (AuditContextBundle.Operation op : AuditContextBundle.Operation.values()) {
            try {
                op.toOngoingStatusOperation();
                actualMappable++;
            } catch (IllegalArgumentException ignored) {
            }
        }
        assertEquals(expectedMappable, actualMappable,
                "A new Operation value was added without updating the mapping in Operation. " +
                "Update TO_MARK_TYPE/FROM_MARK_TYPE maps and this test.");
    }

    @Test
    @DisplayName("All mappable TargetSystemAuditMarkTypes must have a mapping — fails if a new value is added without updating the map")
    void allMappableMarkTypesCovered() {
        int expectedMappable = 2; // APPLIED, ROLLED_BACK — NONE is intentionally unmapped
        int actualMappable = 0;
        for (TargetSystemAuditMarkType mt : TargetSystemAuditMarkType.values()) {
            try {
                AuditContextBundle.Operation.fromOngoingStatusOperation(mt);
                actualMappable++;
            } catch (IllegalArgumentException ignored) {
            }
        }
        assertEquals(expectedMappable, actualMappable,
                "A new TargetSystemAuditMarkType value was added without updating the mapping in Operation. " +
                "Update TO_MARK_TYPE/FROM_MARK_TYPE maps and this test.");
    }
}
