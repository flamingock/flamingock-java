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
package io.flamingock.internal.core.engine.audit.domain;

import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.audit.AuditTxType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AuditEntryInfoTest {

    @Test
    void shouldCreateAuditEntryInfoWithAllFields() {
        // Given
        String changeId = "change-001";
        AuditEntry.Status status = AuditEntry.Status.EXECUTED;
        AuditTxType txType = AuditTxType.TX_SHARED;

        // When
        AuditEntryInfo info = new AuditEntryInfo(changeId, status, txType);

        // Then
        assertEquals(changeId, info.getChangeId());
        assertEquals(status, info.getStatus());
        assertEquals(txType, info.getTxType());
    }

    @Test
    void shouldDefaultToNonTxWhenTxTypeIsNull() {
        // Given
        String changeId = "change-002";
        AuditEntry.Status status = AuditEntry.Status.STARTED;
        AuditTxType txType = null;

        // When
        AuditEntryInfo info = new AuditEntryInfo(changeId, status, txType);

        // Then
        assertEquals(changeId, info.getChangeId());
        assertEquals(status, info.getStatus());
        assertEquals(AuditTxType.NON_TX, info.getTxType());
    }

    @Test
    void shouldImplementEqualsAndHashCodeCorrectly() {
        // Given
        AuditEntryInfo info1 = new AuditEntryInfo("change-001", 
            AuditEntry.Status.EXECUTED, AuditTxType.TX_SHARED);
        AuditEntryInfo info2 = new AuditEntryInfo("change-001", 
            AuditEntry.Status.EXECUTED, AuditTxType.TX_SHARED);
        AuditEntryInfo info3 = new AuditEntryInfo("change-002", 
            AuditEntry.Status.EXECUTED, AuditTxType.TX_SHARED);

        // Then
        assertEquals(info1, info2);
        assertNotEquals(info1, info3);
        assertEquals(info1.hashCode(), info2.hashCode());
        assertNotEquals(info1.hashCode(), info3.hashCode());
    }

    @Test
    void shouldHandleNullInEqualsComparison() {
        // Given
        AuditEntryInfo info = new AuditEntryInfo("change-001", 
            AuditEntry.Status.EXECUTED, AuditTxType.NON_TX);

        // Then
        assertNotEquals(info, null);
        assertNotEquals(info, "not an AuditEntryInfo object");
        assertEquals(info, info); // Self-equality
    }

    @Test
    void shouldHandleNullFieldsInEquals() {
        // Given - info with null changeId
        AuditEntryInfo info1 = new AuditEntryInfo(null, 
            AuditEntry.Status.EXECUTED, AuditTxType.NON_TX);
        AuditEntryInfo info2 = new AuditEntryInfo(null, 
            AuditEntry.Status.EXECUTED, AuditTxType.NON_TX);
        AuditEntryInfo info3 = new AuditEntryInfo("change-001", 
            AuditEntry.Status.EXECUTED, AuditTxType.NON_TX);

        // Then
        assertEquals(info1, info2);
        assertNotEquals(info1, info3);
        assertNotEquals(info3, info1);
    }

    @Test 
    void shouldProvideReadableToString() {
        // Given
        AuditEntryInfo info = new AuditEntryInfo("my-change", 
            AuditEntry.Status.ROLLBACK_FAILED, AuditTxType.TX_SEPARATE_WITH_MARKER);

        // When
        String toString = info.toString();

        // Then
        assertTrue(toString.contains("my-change"));
        assertTrue(toString.contains("ROLLBACK_FAILED"));
        assertTrue(toString.contains("TX_SEPARATE_WITH_MARKER"));
        assertTrue(toString.contains("AuditEntryInfo"));
    }


    @Test
    void shouldHandleAllAuditTxTypeValues() {
        // Given - all possible txType values
        AuditTxType[] allTypes = AuditTxType.values();

        // When & Then - should create info for each type
        for (AuditTxType txType : allTypes) {
            AuditEntryInfo info = new AuditEntryInfo("change-" + txType.name(), 
                AuditEntry.Status.EXECUTED, txType);
            
            assertEquals(txType, info.getTxType());
            assertTrue(info.toString().contains(txType.name()));
        }
    }

    @Test
    void shouldHandleAllAuditStatusValues() {
        // Given - all possible status values  
        AuditEntry.Status[] allStatuses = AuditEntry.Status.values();

        // When & Then - should create info for each status
        for (AuditEntry.Status status : allStatuses) {
            AuditEntryInfo info = new AuditEntryInfo("change-" + status.name(), 
                status, AuditTxType.NON_TX);
            
            assertEquals(status, info.getStatus());
            assertTrue(info.toString().contains(status.name()));
        }
    }
}