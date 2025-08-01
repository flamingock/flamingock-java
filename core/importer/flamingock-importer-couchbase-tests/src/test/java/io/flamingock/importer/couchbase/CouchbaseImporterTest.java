/*
 * Copyright 2023 Flamingock (https://www.flamingock.io)
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
package io.flamingock.importer.couchbase;

import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.Collection;
import com.couchbase.client.java.json.JsonObject;
import com.couchbase.client.java.manager.bucket.BucketManager;
import com.couchbase.client.java.manager.bucket.BucketSettings;
import io.flamingock.api.annotations.EnableFlamingock;
import io.flamingock.api.annotations.Stage;
import io.flamingock.internal.core.runner.Runner;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.couchbase.CouchbaseContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

import static io.flamingock.api.StageType.LEGACY;
import static io.flamingock.api.StageType.SYSTEM;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnableFlamingock(
        stages = {
                @Stage(location = "io.flamingock.importer.couchbase.system", type = SYSTEM),
                @Stage(location = "io.flamingock.importer.couchbase.legacy", type = LEGACY),
                @Stage(location = "io.flamingock.importer.couchbase.couchbase")
        }
)
@Testcontainers
public class CouchbaseImporterTest {

    public static final String MONGOCK_CHANGE_LOGS = "mongockChangeLogs";
    private static final String DB_NAME = "test";


    @Container
    static final CouchbaseContainer couchbaseContainer = new CouchbaseContainer("couchbase/server:7.2.2")
            .withBucket(new org.testcontainers.couchbase.BucketDefinition(DB_NAME));

    private static Cluster cluster;
    private static Bucket bucket;
    private static Collection collection;

    @BeforeAll
    static void setupAll() {
        couchbaseContainer.start();
        cluster = Cluster.connect(
                couchbaseContainer.getConnectionString(),
                couchbaseContainer.getUsername(),
                couchbaseContainer.getPassword()
        );

        bucket = cluster.bucket(DB_NAME);
        collection = cluster.bucket(DB_NAME).defaultCollection();

        BucketManager bucketManager = cluster.buckets();

        int ramQuotaMB = 100;

        if (!bucketManager.getAllBuckets().containsKey(MONGOCK_CHANGE_LOGS)) {
            bucketManager.createBucket(BucketSettings.create(MONGOCK_CHANGE_LOGS).ramQuotaMB(ramQuotaMB));
            cluster.bucket(MONGOCK_CHANGE_LOGS).waitUntilReady(Duration.ofSeconds(10));
        }

        cluster.query("CREATE PRIMARY INDEX IF NOT EXISTS ON `" + MONGOCK_CHANGE_LOGS + "`");
    }

    @BeforeEach
    void cleanUp() {
        cluster.query("DELETE FROM `" + MONGOCK_CHANGE_LOGS + "`");
        cluster.query("DELETE FROM `" + DB_NAME + "`");
    }

    @Test
    void testImporterIntegration() {
        Collection originCollection = cluster.bucket(MONGOCK_CHANGE_LOGS).defaultCollection();
        JsonObject doc = JsonObject.create()
                .put("executionId", "exec-1")
                .put("changeId", "change-1")
                .put("author", "author1")
                .put("timestamp", String.valueOf(Instant.now().toEpochMilli()))
                .put("state", "EXECUTED")
                .put("type", "EXECUTION")
                .put("changeLogClass", "io.flamingock.changelog.Class1")
                .put("changeSetMethod", "method1")
                .putNull("metadata")
                .put("executionMillis", 123L)
                .put("executionHostName", "host1")
                .putNull("errorTrace")
                .put("systemChange", true);
        originCollection.upsert("change-1", doc);

        Runner flamingock = io.flamingock.community.Flamingock.builder()
                .addDependency(cluster)
                .addDependency(collection)
                .addDependency(bucket)
                .disableTransaction()
                .build();

        flamingock.run();

        List<JsonObject> auditLog = cluster.query(
                        "SELECT * FROM `" + DB_NAME + "`"
                )
                .rowsAsObject()
                .stream()
                .map(row -> row.getObject(DB_NAME))
                .collect(Collectors.toList());

        assertTrue(auditLog.size() > 0, "Audit log should not be empty");

        JsonObject entry = auditLog.stream()
                .filter(e -> "change-1".equals(e.getString("changeId")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Entry with changeId 'change-1' not found"));

        assertEquals("change-1", entry.getString("changeId"));
        assertEquals("author1", entry.getString("author"));
        assertEquals("exec-1", entry.getString("executionId"));
        assertEquals("EXECUTED", entry.getString("state"));
        assertTrue(entry.getBoolean("systemChange"));
    }

    @Test
    void failIfEmptyOrigin() {
        Runner flamingock = io.flamingock.community.Flamingock.builder()
                .addDependency(cluster)
                .addDependency(collection)
                .addDependency(bucket)
                .disableTransaction()
                .build();

        org.junit.jupiter.api.Assertions.assertThrows(
                io.flamingock.internal.common.core.error.FlamingockException.class,
                flamingock::run
        );
    }
}