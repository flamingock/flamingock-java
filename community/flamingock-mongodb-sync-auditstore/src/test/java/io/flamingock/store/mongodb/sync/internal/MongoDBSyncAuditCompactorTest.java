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
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.result.DeleteResult;
import io.flamingock.api.RecoveryStrategy;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.audit.AuditTxType;
import io.flamingock.internal.common.mongodb.MongoDBAuditMapper;
import io.flamingock.internal.common.mongodb.MongoDBDocumentHelper;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit coverage for {@link MongoDBSyncAuditCompactor} against a mocked collection.
 * <p>
 * Smaller than the DynamoDB equivalent on purpose. DynamoDB rekeys, so ordering, paging, survivor
 * identity, key collisions and crash convergence are each a live risk there. Here nothing is written and
 * the survivor is excluded from every delete by its primary key, so what remains to get wrong is narrow:
 * the wrong documents are targeted, something gets written, or a failure is mishandled.
 * <p>
 * The one assertion that earns its white-box cost is {@link #theDeleteFilterIsScopedAndExcludesTheSurvivor()}.
 * An earlier version of this class identified the survivor by its {@code (executionId, state)} pair rather
 * than by {@code _id}; dropping the {@code executionId} half of that predicate silently spared extra
 * documents and <em>survived every test in this module and in the shared conformance suite</em>. Addressing
 * the survivor by primary key removed that failure mode rather than testing around it, and this assertion
 * pins the filter that replaced it.
 * <p>
 * Note this is the first test in the repo to mock a {@code MongoCollection}. The end state these tests
 * imply is covered against a real MongoDB by
 * {@code io.flamingock.store.mongodb.sync.MongoDBSyncAuditCompactionConformanceTest}.
 */
class MongoDBSyncAuditCompactorTest {

    private static final String CHANGE_A = "change-a";
    private static final String CHANGE_B = "change-b";
    private static final LocalDateTime T0 = LocalDateTime.of(2026, 1, 1, 12, 0, 0);
    private static final LocalDateTime T1 = LocalDateTime.of(2026, 1, 1, 12, 5, 0);

    private final MongoDBAuditMapper<MongoDBDocumentHelper> mapper =
            new MongoDBAuditMapper<>(() -> new MongoDBDocumentHelper(new Document()));

    @Test
    @DisplayName("one delete per change that has superseded documents")
    void deletesOneBatchPerChangeThatNeedsIt() {
        MongoCollection<Document> collection = collectionHolding(
                stored("id-a1", CHANGE_A, "exec-1", AuditEntry.Status.STARTED, T0),
                stored("id-a2", CHANGE_A, "exec-1", AuditEntry.Status.APPLIED, T1),
                stored("id-b1", CHANGE_B, "exec-1", AuditEntry.Status.STARTED, T0),
                stored("id-b2", CHANGE_B, "exec-1", AuditEntry.Status.APPLIED, T1));

        assertOk(new MongoDBSyncAuditCompactor(collection, mapper).compact());

        Mockito.verify(collection, Mockito.times(2)).deleteMany(Mockito.any(Bson.class));
    }

    @Test
    @DisplayName("nothing is ever written — only the read and the deletes reach the collection")
    void neverWritesAnything() {
        MongoCollection<Document> collection = collectionHolding(
                stored("id-a1", CHANGE_A, "exec-1", AuditEntry.Status.STARTED, T0),
                stored("id-a2", CHANGE_A, "exec-1", AuditEntry.Status.APPLIED, T1));

        assertOk(new MongoDBSyncAuditCompactor(collection, mapper).compact());

        // Proves three contract clauses by construction rather than by inspecting state afterwards: the
        // survivor is preserved verbatim because it is never rewritten, no index or schema is altered, and
        // no journal event is emitted. There is no other call the compactor can make.
        Mockito.verify(collection).find();
        Mockito.verify(collection).deleteMany(Mockito.any(Bson.class));
        Mockito.verifyNoMoreInteractions(collection);
    }

    @Test
    @DisplayName("the delete filter is scoped to the change and excludes the survivor by _id")
    void theDeleteFilterIsScopedAndExcludesTheSurvivor() {
        MongoCollection<Document> collection = collectionHolding(
                stored("id-superseded", CHANGE_A, "exec-1", AuditEntry.Status.STARTED, T0),
                stored("id-survivor", CHANGE_A, "exec-1", AuditEntry.Status.APPLIED, T1));

        assertOk(new MongoDBSyncAuditCompactor(collection, mapper).compact());

        String rendered = capturedFilter(collection).toJson();
        // Scope: without it, `_id != survivor` would match almost the whole collection.
        assertTrue(rendered.contains("\"changeId\"") && rendered.contains(CHANGE_A),
                "the filter must be scoped to the change: " + rendered);
        // Exclusion: by identity, so there is exactly one predicate and no attribute combination to get
        // wrong. `id-survivor` must be the one spared, not the superseded document.
        assertTrue(rendered.contains("$ne") && rendered.contains("id-survivor"),
                "the survivor must be excluded by _id: " + rendered);
        assertTrue(!rendered.contains("id-superseded"),
                "the superseded document must not appear in the exclusion: " + rendered);
    }

    @Test
    @DisplayName("on an exact createdAt tie the higher status priority survives")
    void survivorIsChosenByStatusPriorityOnTie() {
        MongoCollection<Document> collection = collectionHolding(
                stored("id-applied", CHANGE_A, "exec-1", AuditEntry.Status.APPLIED, T0),
                stored("id-rolled-back", CHANGE_A, "exec-1", AuditEntry.Status.ROLLED_BACK, T0));

        assertOk(new MongoDBSyncAuditCompactor(collection, mapper).compact());

        String rendered = capturedFilter(collection).toJson();
        assertTrue(rendered.contains("id-rolled-back"),
                "ROLLED_BACK outranks APPLIED on an equal createdAt; selecting on createdAt alone would"
                        + " resolve this arbitrarily: " + rendered);
    }

    @Test
    @DisplayName("a change that already has one document is left alone")
    void singleDocumentChangeIsLeftAlone() {
        MongoCollection<Document> collection = collectionHolding(
                stored("id-a1", CHANGE_A, "exec-1", AuditEntry.Status.APPLIED, T0));

        assertOk(new MongoDBSyncAuditCompactor(collection, mapper).compact());

        Mockito.verify(collection).find();
        Mockito.verify(collection, Mockito.never()).deleteMany(Mockito.any(Bson.class));
    }

    @Test
    @DisplayName("an empty collection is left completely alone")
    void emptyCollectionDoesNothing() {
        MongoCollection<Document> collection = collectionHolding();

        assertOk(new MongoDBSyncAuditCompactor(collection, mapper).compact());

        Mockito.verify(collection).find();
        Mockito.verifyNoMoreInteractions(collection);
    }

    @Test
    @DisplayName("a failed delete is reported, not thrown")
    void failureIsReportedNotThrown() {
        MongoCollection<Document> collection = collectionHolding(
                stored("id-a1", CHANGE_A, "exec-1", AuditEntry.Status.STARTED, T0),
                stored("id-a2", CHANGE_A, "exec-1", AuditEntry.Status.APPLIED, T1));
        Mockito.doThrow(new IllegalStateException("delete failed"))
                .when(collection).deleteMany(Mockito.any(Bson.class));

        assertTrue(new MongoDBSyncAuditCompactor(collection, mapper).compact().isError(),
                "the caller decides what a failed compaction means");
    }

    @Test
    @DisplayName("the first failing change stops the rest")
    void failFastStopsAtTheFirstFailingChange() {
        MongoCollection<Document> collection = collectionHolding(
                stored("id-a1", CHANGE_A, "exec-1", AuditEntry.Status.STARTED, T0),
                stored("id-a2", CHANGE_A, "exec-1", AuditEntry.Status.APPLIED, T1),
                stored("id-b1", CHANGE_B, "exec-1", AuditEntry.Status.STARTED, T0),
                stored("id-b2", CHANGE_B, "exec-1", AuditEntry.Status.APPLIED, T1));
        Mockito.doThrow(new IllegalStateException("delete failed"))
                .when(collection).deleteMany(Mockito.any(Bson.class));

        assertTrue(new MongoDBSyncAuditCompactor(collection, mapper).compact().isError());

        // One attempt only. Nothing was written, so anything already compacted stays compacted and a
        // re-run finishes the job.
        Mockito.verify(collection, Mockito.times(1)).deleteMany(Mockito.any(Bson.class));
    }

    @Test
    @DisplayName("a failed read is reported without deleting anything")
    void readFailureIsReportedWithoutDeleting() {
        @SuppressWarnings("unchecked")
        MongoCollection<Document> collection = Mockito.mock(MongoCollection.class);
        Mockito.when(collection.find()).thenThrow(new IllegalStateException("read failed"));

        assertTrue(new MongoDBSyncAuditCompactor(collection, mapper).compact().isError());

        // Relevant because a document missing its state makes the mapping or aggregation throw. Failing
        // before any delete is what makes that safe rather than destructive.
        Mockito.verify(collection, Mockito.never()).deleteMany(Mockito.any(Bson.class));
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private static MongoCollection<Document> collectionHolding(Document... documents) {
        MongoCollection<Document> collection = Mockito.mock(MongoCollection.class);
        FindIterable<Document> findIterable = Mockito.mock(FindIterable.class);
        Mockito.when(collection.find()).thenReturn(findIterable);
        // The production code reads with find().into(...), the same idiom as
        // MongoDBSyncAuditRepository.getAuditHistory(), so the returned collection is what it consumes.
        Mockito.when(findIterable.into(Mockito.any(ArrayList.class)))
                .thenReturn(new ArrayList<>(Arrays.asList(documents)));
        Mockito.when(collection.deleteMany(Mockito.any(Bson.class)))
                .thenReturn(DeleteResult.acknowledged(1));
        return collection;
    }

    /**
     * Renders the captured delete filter to BSON.
     * <p>
     * The driver here is 4.0.0, where {@code Bson} has only the two-argument
     * {@code toBsonDocument(Class, CodecRegistry)} — the no-argument overload arrived in 4.2.
     */
    private static BsonDocument capturedFilter(MongoCollection<Document> collection) {
        ArgumentCaptor<Bson> captor = ArgumentCaptor.forClass(Bson.class);
        Mockito.verify(collection).deleteMany(captor.capture());
        return captor.getValue()
                .toBsonDocument(Document.class, MongoClientSettings.getDefaultCodecRegistry());
    }

    /** A stored audit document, with the {@code _id} the real collection would have assigned. */
    private Document stored(String id,
                            String changeId,
                            String executionId,
                            AuditEntry.Status status,
                            LocalDateTime createdAt) {
        Document document = mapper.toDocument(new AuditEntry(
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
                null)).getDocument();
        document.put(MongoDBSyncAuditCompactor.KEY_ID, id);
        return document;
    }

    private static void assertOk(Result result) {
        if (result.isError()) {
            throw new AssertionError("compact() failed: " + ((Result.Error) result).getError());
        }
    }
}
