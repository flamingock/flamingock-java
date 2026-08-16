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
package io.flamingock.store.mongodb.reactive.internal;

import com.mongodb.ReadConcern;
import com.mongodb.ReadPreference;
import com.mongodb.WriteConcern;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.mongodb.reactivestreams.client.MongoDatabase;
import io.flamingock.api.RecoveryStrategy;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.audit.AuditTxType;
import io.flamingock.internal.core.configuration.community.CommunityConfigurable;
import io.flamingock.internal.util.id.RunnerId;
import io.flamingock.reactive.util.PublisherSync;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

@Testcontainers
class MongoDBReactiveAuditPersistenceTest {

    private static final String DB_NAME = "test";
    private static final String AUDIT_COLLECTION = "testFlamingockAudit";

    @Container
    static MongoDBContainer mongoDBContainer = new MongoDBContainer(DockerImageName.parse("mongo:6"));

    private MongoClient mongoClient;
    private MongoDatabase database;
    private MongoDBReactiveAuditPersistence persistence;

    @BeforeEach
    void beforeEach() {
        mongoClient = MongoClients.create(mongoDBContainer.getConnectionString());
        database = mongoClient.getDatabase(DB_NAME);
        persistence = new MongoDBReactiveAuditPersistence(
                mock(CommunityConfigurable.class),
                database,
                AUDIT_COLLECTION,
                ReadConcern.MAJORITY,
                ReadPreference.primary(),
                WriteConcern.MAJORITY.withJournal(true),
                true);
        persistence.initialize(RunnerId.fromString("runner-1"));
    }

    @AfterEach
    void afterEach() {
        PublisherSync.complete(database.drop());
        mongoClient.close();
    }

    @Test
    @DisplayName("Should write and read audit entries")
    void shouldWriteAndReadAuditEntries() {
        AuditEntry started = auditEntry("change-1", AuditEntry.Status.STARTED);
        AuditEntry applied = auditEntry("change-1", AuditEntry.Status.APPLIED);

        persistence.writeEntry(started);
        persistence.writeEntry(applied);

        List<AuditEntry> history = persistence.getAuditHistory();
        List<AuditEntry.Status> states = history.stream()
                .map(AuditEntry::getState)
                .collect(Collectors.toList());

        assertEquals(2, history.size());
        assertTrue(states.contains(AuditEntry.Status.STARTED));
        assertTrue(states.contains(AuditEntry.Status.APPLIED));
        assertTrue(history.stream().allMatch(entry -> "change-1".equals(entry.getChangeId())));
    }

    @Test
    @DisplayName("Should replace an existing audit entry with the same execution, change and state")
    void shouldReplaceExistingAuditEntry() {
        persistence.writeEntry(auditEntry("change-1", AuditEntry.Status.STARTED));
        persistence.writeEntry(auditEntry("change-1", AuditEntry.Status.STARTED));

        assertEquals(1, persistence.getAuditHistory().size());
    }

    private AuditEntry auditEntry(String changeId, AuditEntry.Status status) {
        return new AuditEntry(
                "execution-1",
                "stage-1",
                changeId,
                "test-author",
                LocalDateTime.now(),
                status,
                AuditEntry.ChangeType.STANDARD_CODE,
                "TestChange",
                "apply",
                "TestChange.java",
                10L,
                "localhost",
                null,
                false,
                null,
                AuditTxType.NON_TX,
                "mongodb",
                "001",
                RecoveryStrategy.MANUAL_INTERVENTION,
                false);
    }
}
