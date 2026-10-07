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
package io.flamingock.store.mongodb.sync.internal;

import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.result.DeleteResult;
import io.flamingock.api.RecoveryStrategy;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.audit.AuditReader;
import io.flamingock.internal.common.core.audit.AuditTxType;
import io.flamingock.internal.util.Result;
import org.bson.BsonDocument;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit coverage for {@link MongoDBSyncAuditCompactor} against a mocked collection.
 * <p>
 * Deliberately smaller than the DynamoDB equivalent, and that is the point rather than an omission.
 * DynamoDB rekeys, so ordering, paging, survivor identity, key collisions and crash convergence are all
 * live risks there and each needs its own test. Here nothing is written and the survivor is excluded from
 * every delete by construction, so the only things that can go wrong are: the wrong documents are
 * targeted, something gets written that should not, or a failure is mishandled. Those are what follows.
 * <p>
 * The {@link AuditReader} needs no mocking framework — it is a functional interface whose single abstract
 * method is {@code getAuditHistory()}, so a lambda is the whole stub. Note this is the first test in the
 * repo to mock a {@code MongoCollection}; the end state it implies is covered against a real MongoDB by
 * {@code io.flamingock.store.mongodb.sync.MongoDBSyncAuditCompactionConformanceTest}.
 */
class MongoDBSyncAuditCompactorTest {

    private static final String CHANGE_A = "change-a";
    private static final String CHANGE_B = "change-b";
    private static final LocalDateTime T0 = LocalDateTime.of(2026, 1, 1, 12, 0, 0);
    private static final LocalDateTime T1 = LocalDateTime.of(2026, 1, 1, 12, 5, 0);

    @Test
    @DisplayName("one delete per change in the snapshot")
    void deletesOneBatchPerChange() {
        MongoCollection<Document> collection = collectionDeleting(1);
        AuditCompactorUnderTest compactor = compactorOver(collection,
                entry(CHANGE_A, "exec-1", AuditEntry.Status.STARTED, T0),
                entry(CHANGE_A, "exec-1", AuditEntry.Status.APPLIED, T1),
                entry(CHANGE_B, "exec-1", AuditEntry.Status.APPLIED, T0));

        assertOk(compactor.compact());

        Mockito.verify(collection, Mockito.times(2)).deleteMany(Mockito.any(Bson.class));
    }

    @Test
    @DisplayName("nothing is ever written — only deletes reach the collection")
    void neverWritesAnything() {
        MongoCollection<Document> collection = collectionDeleting(1);
        AuditCompactorUnderTest compactor = compactorOver(collection,
                entry(CHANGE_A, "exec-1", AuditEntry.Status.STARTED, T0),
                entry(CHANGE_A, "exec-1", AuditEntry.Status.APPLIED, T1));

        assertOk(compactor.compact());

        // Proves three contract clauses by construction rather than by inspecting state afterwards: the
        // survivor is preserved verbatim (never rewritten), no index or schema is altered, and no journal
        // event is emitted. There is simply no other call the compactor can make.
        Mockito.verify(collection).deleteMany(Mockito.any(Bson.class));
        Mockito.verifyNoMoreInteractions(collection);
    }

    @Test
    @DisplayName("the delete filter targets the change and excludes its surviving document")
    void theSurvivorIsExcludedFromTheDeleteFilter() {
        MongoCollection<Document> collection = collectionDeleting(1);
        AuditCompactorUnderTest compactor = compactorOver(collection,
                entry(CHANGE_A, "exec-1", AuditEntry.Status.STARTED, T0),
                entry(CHANGE_A, "exec-1", AuditEntry.Status.APPLIED, T1));

        assertOk(compactor.compact());

        // White-box, and justified: this is the one filter whose inversion would delete the survivor
        // instead of its superseded siblings, which is the single unrecoverable outcome in the contract.
        BsonDocument filter = renderFilter(collection);
        String rendered = filter.toJson();
        assertTrue(rendered.contains("\"changeId\""), "filter must be scoped to the change: " + rendered);
        assertTrue(rendered.contains("$nor"), "the survivor must be excluded, not matched: " + rendered);
        assertTrue(rendered.contains("APPLIED"),
                "the excluded document must be the surviving APPLIED state, not the superseded one: "
                        + rendered);
        assertTrue(!rendered.contains("STARTED"),
                "the superseded state must not appear in the exclusion: " + rendered);
    }

    @Test
    @DisplayName("an empty store is left completely alone")
    void emptyStoreDoesNothing() {
        MongoCollection<Document> collection = collectionDeleting(0);
        AuditCompactorUnderTest compactor = compactorOver(collection);

        assertOk(compactor.compact());

        Mockito.verifyNoInteractions(collection);
    }

    @Test
    @DisplayName("a failure is reported, not thrown")
    void failureIsReportedNotThrown() {
        MongoCollection<Document> collection = collectionDeleting(1);
        Mockito.doThrow(new IllegalStateException("delete failed"))
                .when(collection).deleteMany(Mockito.any(Bson.class));
        AuditCompactorUnderTest compactor = compactorOver(collection,
                entry(CHANGE_A, "exec-1", AuditEntry.Status.STARTED, T0),
                entry(CHANGE_A, "exec-1", AuditEntry.Status.APPLIED, T1));

        Result result = compactor.compact();

        assertTrue(result.isError(), "the caller decides what a failed compaction means");
    }

    @Test
    @DisplayName("the first failing change stops the rest")
    void failFastStopsAtTheFirstFailingChange() {
        MongoCollection<Document> collection = collectionDeleting(1);
        Mockito.doThrow(new IllegalStateException("delete failed"))
                .when(collection).deleteMany(Mockito.any(Bson.class));
        AuditCompactorUnderTest compactor = compactorOver(collection,
                entry(CHANGE_A, "exec-1", AuditEntry.Status.STARTED, T0),
                entry(CHANGE_A, "exec-1", AuditEntry.Status.APPLIED, T1),
                entry(CHANGE_B, "exec-1", AuditEntry.Status.STARTED, T0),
                entry(CHANGE_B, "exec-1", AuditEntry.Status.APPLIED, T1));

        assertTrue(compactor.compact().isError());

        // One attempt only. Nothing was written, so the changes already compacted stay compacted and a
        // re-run finishes the job.
        Mockito.verify(collection, Mockito.times(1)).deleteMany(Mockito.any(Bson.class));
    }

    @Test
    @DisplayName("a reader failure is reported without touching the collection")
    void readerFailureIsReportedWithoutDeleting() {
        MongoCollection<Document> collection = collectionDeleting(1);
        MongoDBSyncAuditCompactor compactor = new MongoDBSyncAuditCompactor(collection, () -> {
            throw new IllegalStateException("read failed");
        });

        assertTrue(compactor.compact().isError());

        // Relevant because a document missing its state makes the snapshot aggregation throw. Failing
        // before any delete is what makes that safe rather than destructive.
        Mockito.verifyNoInteractions(collection);
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------

    /** Narrow alias so the tests read as being about the capability, not the concrete class. */
    private interface AuditCompactorUnderTest {
        Result compact();
    }

    private static AuditCompactorUnderTest compactorOver(MongoCollection<Document> collection,
                                                         AuditEntry... history) {
        final List<AuditEntry> entries = Arrays.asList(history);
        MongoDBSyncAuditCompactor compactor =
                new MongoDBSyncAuditCompactor(collection, () -> new ArrayList<>(entries));
        return compactor::compact;
    }

    @SuppressWarnings("unchecked")
    private static MongoCollection<Document> collectionDeleting(long deletedCount) {
        MongoCollection<Document> collection = Mockito.mock(MongoCollection.class);
        Mockito.when(collection.deleteMany(Mockito.any(Bson.class)))
                .thenReturn(DeleteResult.acknowledged(deletedCount));
        return collection;
    }

    /**
     * Renders the captured filter to BSON so it can be inspected.
     * <p>
     * The driver here is 4.0.0, where {@code Bson} has only the two-argument
     * {@code toBsonDocument(Class, CodecRegistry)} — the no-argument convenience overload arrived in 4.2.
     */
    private static BsonDocument renderFilter(MongoCollection<Document> collection) {
        ArgumentCaptor<Bson> captor = ArgumentCaptor.forClass(Bson.class);
        Mockito.verify(collection).deleteMany(captor.capture());
        return captor.getValue()
                .toBsonDocument(Document.class, MongoClientSettings.getDefaultCodecRegistry());
    }

    private static AuditEntry entry(String changeId,
                                    String executionId,
                                    AuditEntry.Status status,
                                    LocalDateTime createdAt) {
        return new AuditEntry(
                executionId,
                "test-stage",
                changeId,
                "test-author",
                createdAt,
                status,
                AuditEntry.ChangeType.STANDARD_CODE,
                "TestChangeClass",
                "testMethod",
                "TestSourceFile",
                0L,
                "localhost",
                null,
                false,
                null,
                AuditTxType.NON_TX,
                "test-target-system",
                "001",
                RecoveryStrategy.MANUAL_INTERVENTION,
                null);
    }

    private static void assertOk(Result result) {
        if (result.isError()) {
            throw new AssertionError("compact() failed: " + ((Result.Error) result).getError());
        }
    }
}
