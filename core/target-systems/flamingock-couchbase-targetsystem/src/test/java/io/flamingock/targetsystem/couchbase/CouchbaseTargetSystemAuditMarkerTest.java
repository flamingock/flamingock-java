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
package io.flamingock.targetsystem.couchbase;

import com.couchbase.client.core.io.CollectionIdentifier;
import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.transactions.TransactionAttemptContext;
import io.flamingock.internal.common.core.targets.TargetSystemAuditMarkType;
import io.flamingock.internal.core.external.targets.mark.TargetSystemAuditMark;
import io.flamingock.internal.core.transaction.TransactionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.couchbase.BucketDefinition;
import org.testcontainers.couchbase.CouchbaseContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Direct write-verification for {@link CouchbaseTargetSystemAuditMarker}. The existing
 * {@code CouchbaseTargetSystemTest} only asserts the end-state marker count is zero, which is
 * also satisfied when {@code mark()} is a no-op. This test exercises {@code mark()},
 * {@code listAll()} and {@code clearMark()} through their production code paths, proving the
 * write actually persists.
 *
 * <p>Couchbase's marker {@code mark()} requires a live {@link TransactionAttemptContext} from
 * the {@link TransactionManager}. Each call is therefore wrapped in a real Couchbase transaction
 * (`cluster.transactions().run(...)`), registering the attempt context under the changeId before
 * invoking the marker and unregistering after.
 */
@Testcontainers
public class CouchbaseTargetSystemAuditMarkerTest {

    private static final String BUCKET_NAME = "test";
    private static final String SCOPE_NAME = CollectionIdentifier.DEFAULT_SCOPE;
    private static final String MARKER_COLLECTION = "flamingockAuditMarkerTest";

    @Container
    public static final CouchbaseContainer couchbaseContainer = new CouchbaseContainer("couchbase/server:7.2.4")
            .withBucket(new BucketDefinition(BUCKET_NAME));

    private static Cluster cluster;
    private static Bucket bucket;

    private TransactionManager<TransactionAttemptContext> txManager;
    private CouchbaseTargetSystemAuditMarker marker;

    @BeforeAll
    static void beforeAll() {
        couchbaseContainer.start();
        cluster = Cluster.connect(
                couchbaseContainer.getConnectionString(),
                couchbaseContainer.getUsername(),
                couchbaseContainer.getPassword());

        bucket = cluster.bucket(BUCKET_NAME);
        bucket.waitUntilReady(Duration.ofSeconds(10));
    }

    @BeforeEach
    void beforeEach() {
        txManager = new TransactionManager<>(() -> {
            throw new UnsupportedOperationException(
                    "Supplier is unused: Couchbase tests register the TransactionAttemptContext explicitly via startSession(id, ctx)");
        });

        marker = CouchbaseTargetSystemAuditMarker.builder(cluster, bucket, txManager)
                .withScopeName(SCOPE_NAME)
                .withCollectionName(MARKER_COLLECTION)
                .withAutoCreate(true)
                .build();

        // Start each test from an empty marker collection.
        clearAll();
    }

    @AfterEach
    void afterEach() {
        clearAll();
    }

    @Test
    @DisplayName("mark() persists each mark and listAll() returns them with the right contents")
    void markPersistsAndIsReadableViaListAll() {
        String changeId1 = "change-1";
        String changeId2 = "change-2";

        markInTransaction(changeId1, TargetSystemAuditMarkType.APPLIED);
        markInTransaction(changeId2, TargetSystemAuditMarkType.ROLLED_BACK);

        Set<TargetSystemAuditMark> marks = marker.listAll();
        Assertions.assertEquals(2, marks.size());

        Map<String, TargetSystemAuditMarkType> byId = marks.stream()
                .collect(Collectors.toMap(TargetSystemAuditMark::getChangeId,
                        TargetSystemAuditMark::getOperation));
        Assertions.assertEquals(TargetSystemAuditMarkType.APPLIED, byId.get(changeId1));
        Assertions.assertEquals(TargetSystemAuditMarkType.ROLLED_BACK, byId.get(changeId2));
    }

    @Test
    @DisplayName("clearMark() removes only the targeted mark")
    void clearMarkRemovesOnlyTheTargetedMark() {
        String changeId1 = "change-1";
        String changeId2 = "change-2";
        markInTransaction(changeId1, TargetSystemAuditMarkType.APPLIED);
        markInTransaction(changeId2, TargetSystemAuditMarkType.APPLIED);

        marker.clearMark(changeId1);

        Set<TargetSystemAuditMark> marks = marker.listAll();
        Assertions.assertEquals(1, marks.size());
        Assertions.assertEquals(changeId2, marks.iterator().next().getChangeId());
    }

    private void markInTransaction(String changeId, TargetSystemAuditMarkType operation) {
        cluster.transactions().run(ctx -> {
            txManager.startSession(changeId, ctx);
            marker.mark(new TargetSystemAuditMark(changeId, operation));
            txManager.closeSession(changeId);
        });
    }

    private void clearAll() {
        for (TargetSystemAuditMark mark : marker.listAll()) {
            marker.clearMark(mark.getChangeId());
        }
    }
}
