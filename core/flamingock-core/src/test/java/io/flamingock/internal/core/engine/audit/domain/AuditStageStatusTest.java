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

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AuditStageStatusTest {

    @Test
    void shouldBuildWithEntryBuilderAndProvideAllMaps() {
        // Given
        AuditStageStatus.EntryBuilder builder = AuditStageStatus.entryBuilder();
        
        AuditEntry entry1 = createAuditEntry("change-1", AuditEntry.Status.EXECUTED, AuditTxType.NON_TX);
        AuditEntry entry2 = createAuditEntry("change-2", AuditEntry.Status.STARTED, AuditTxType.TX_SHARED);
        
        // When
        builder.addEntry(entry1);
        builder.addEntry(entry2);
        AuditStageStatus status = builder.build();

        // Then - Entry info map should work  
        Map<String, AuditEntryInfo> infoMap = status.getEntryInfoMap();
        assertEquals(2, infoMap.size());
        
        AuditEntryInfo info1 = infoMap.get("change-1");
        assertEquals("change-1", info1.getChangeId());
        assertEquals(AuditEntry.Status.EXECUTED, info1.getStatus());
        assertEquals(AuditTxType.NON_TX, info1.getTxType());

        AuditEntryInfo info2 = infoMap.get("change-2");
        assertEquals("change-2", info2.getChangeId());
        assertEquals(AuditEntry.Status.STARTED, info2.getStatus());
        assertEquals(AuditTxType.TX_SHARED, info2.getTxType());
    }

    @Test
    void shouldHandleMostRelevantEntryInBuilder() {
        // Given - Two entries for same change, second one is more recent
        AuditStageStatus.EntryBuilder builder = AuditStageStatus.entryBuilder();
        
        AuditEntry olderEntry = createAuditEntry("change-1", AuditEntry.Status.STARTED, AuditTxType.NON_TX, 
                LocalDateTime.now().minusMinutes(10));
        AuditEntry newerEntry = createAuditEntry("change-1", AuditEntry.Status.EXECUTED, AuditTxType.TX_SHARED,
                LocalDateTime.now());
        
        // When
        builder.addEntry(olderEntry);
        builder.addEntry(newerEntry);
        AuditStageStatus status = builder.build();

        // Then - Should use the most relevant (newer) entry
        AuditEntryInfo info = status.getEntryInfoMap().get("change-1");
        assertEquals(AuditEntry.Status.EXECUTED, info.getStatus());
        assertEquals(AuditTxType.TX_SHARED, info.getTxType());
    }

    @Test
    void shouldHandleNullTxTypeInEntries() {
        // Given - Entry with null txType
        AuditStageStatus.EntryBuilder builder = AuditStageStatus.entryBuilder();
        AuditEntry entryWithNullTxType = createAuditEntry("change-1", AuditEntry.Status.EXECUTED, null);
        
        // When
        builder.addEntry(entryWithNullTxType);
        AuditStageStatus status = builder.build();

        // Then - Should default to NON_TX
        AuditEntryInfo info = status.getEntryInfoMap().get("change-1");
        assertEquals(AuditTxType.NON_TX, info.getTxType());
    }


    @Test
    void shouldMaintainConsistencyBetweenMaps() {
        // Given
        AuditStageStatus.EntryBuilder builder = AuditStageStatus.entryBuilder();
        
        for (int i = 1; i <= 5; i++) {
            AuditEntry entry = createAuditEntry("change-" + i, 
                AuditEntry.Status.EXECUTED, AuditTxType.TX_SHARED);
            builder.addEntry(entry);
        }
        
        // When
        AuditStageStatus status = builder.build();

        // Then - Entry info map should contain all entries with correct data
        Map<String, AuditEntryInfo> infoMap = status.getEntryInfoMap();
        
        assertEquals(5, infoMap.size());
        
        for (int i = 1; i <= 5; i++) {
            String changeId = "change-" + i;
            AuditEntryInfo info = infoMap.get(changeId);
            assertNotNull(info, "Entry info should exist for " + changeId);
            assertEquals(AuditEntry.Status.EXECUTED, info.getStatus());
            assertEquals(AuditTxType.TX_SHARED, info.getTxType());
        }
    }

    private AuditEntry createAuditEntry(String taskId, AuditEntry.Status status, AuditTxType txType) {
        return createAuditEntry(taskId, status, txType, LocalDateTime.now());
    }

    private AuditEntry createAuditEntry(String taskId, AuditEntry.Status status, AuditTxType txType, LocalDateTime timestamp) {
        return new AuditEntry(
                "test-execution",
                "test-stage",
                taskId,
                "test-author",
                timestamp,
                status,
                AuditEntry.ExecutionType.EXECUTION,
                "TestClass",
                "testMethod",
                100L,
                "localhost",
                new HashMap<>(),
                false,
                null,
                txType
        );
    }
}