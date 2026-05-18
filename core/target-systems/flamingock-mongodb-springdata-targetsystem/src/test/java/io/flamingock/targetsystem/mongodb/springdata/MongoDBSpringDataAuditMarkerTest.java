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
package io.flamingock.targetsystem.mongodb.springdata;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import io.flamingock.internal.common.core.targets.TargetSystemAuditMarkType;
import io.flamingock.internal.core.external.targets.mark.TargetSystemAuditMark;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Direct write-verification for {@link MongoDBSpringDataAuditMarker}. The existing
 * {@code MongoDBSpringDataTargetSystemTest} only asserts the end-state marker count is zero,
 * which is also satisfied when {@code mark()} is a no-op. This test exercises {@code mark()},
 * {@code listAll()} and {@code clearMark()} through their production code paths, proving the
 * write actually persists.
 *
 * <p>The Spring Data marker relies on Spring's {@code MongoTransactionManager} for session
 * binding. Outside a Spring transaction, {@code mongoTemplate.getDb()} returns the standard
 * (non-session-bound) {@code MongoDatabase}; the upsert from {@code mark()} still commits
 * immediately. The test exploits that to drive {@code mark()} without orchestrating a Spring
 * transaction.
 */
@Testcontainers
public class MongoDBSpringDataAuditMarkerTest {

    private static final String DB_NAME = "test";
    private static final String MARKER_COLLECTION = "flamingockAuditMarkerTest";

    @Container
    public static final MongoDBContainer mongoDBContainer = new MongoDBContainer(DockerImageName.parse("mongo:6"));

    private static MongoTemplate mongoTemplate;

    private MongoDBSpringDataAuditMarker marker;

    @BeforeAll
    static void beforeAll() {
        MongoClient mongoClient = MongoClients.create(MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(mongoDBContainer.getConnectionString()))
                .build());
        mongoTemplate = new MongoTemplate(mongoClient, DB_NAME);
    }

    @BeforeEach
    void beforeEach() {
        marker = MongoDBSpringDataAuditMarker.builder(mongoTemplate)
                .setCollectionName(MARKER_COLLECTION)
                .withAutoCreate(true)
                .build();
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

        marker.mark(new TargetSystemAuditMark(changeId1, TargetSystemAuditMarkType.APPLIED));
        marker.mark(new TargetSystemAuditMark(changeId2, TargetSystemAuditMarkType.ROLLED_BACK));

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
        marker.mark(new TargetSystemAuditMark(changeId1, TargetSystemAuditMarkType.APPLIED));
        marker.mark(new TargetSystemAuditMark(changeId2, TargetSystemAuditMarkType.APPLIED));

        marker.clearMark(changeId1);

        Set<TargetSystemAuditMark> marks = marker.listAll();
        Assertions.assertEquals(1, marks.size());
        Assertions.assertEquals(changeId2, marks.iterator().next().getChangeId());
    }

    private void clearAll() {
        for (TargetSystemAuditMark mark : marker.listAll()) {
            marker.clearMark(mark.getChangeId());
        }
    }
}
