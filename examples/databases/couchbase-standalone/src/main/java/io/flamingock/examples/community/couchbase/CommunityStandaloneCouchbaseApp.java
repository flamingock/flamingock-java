/*
 * Copyright 2023 Flamingock (https://oss.flamingock.io)
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

package io.flamingock.examples.community.couchbase;

import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.Collection;

import io.flamingock.core.configurator.standalone.FlamingockStandalone;
import io.flamingock.core.pipeline.Stage;
import io.flamingock.examples.community.couchbase.events.FailureEventListener;
import io.flamingock.examples.community.couchbase.events.StartedEventListener;
import io.flamingock.examples.community.couchbase.events.SuccessEventListener;
import io.flamingock.oss.driver.couchbase.driver.CouchbaseDriver;

public class CommunityStandaloneCouchbaseApp {

    private static final String BUCKET_NAME = "bucket";

    public static void main(String[] args) {
        new CommunityStandaloneCouchbaseApp().run(connect(), BUCKET_NAME);
    }

    public void run(Cluster cluster, String bucketName) {
        Collection collection = cluster.bucket(bucketName).defaultCollection();
        FlamingockStandalone.local()
                .setDriver(new CouchbaseDriver(cluster, collection))
                .setLockAcquiredForMillis(60 * 1000L)// this is just to show how is set. Default value is still 60 * 1000L
                .setLockQuitTryingAfterMillis(3 * 60 * 1000L)// this is just to show how is set. Default value is still 3 * 60 * 1000L
                .setLockTryFrequencyMillis(1000L)// this is just to show how is set. Default value is still 1000L
                .addStage(new Stage("stage-name").addCodePackage("io.flamingock.examples.community.couchbase.changes"))
                .addDependency(cluster)
                .addDependency(collection)
                .setTrackIgnored(true)
                .setTransactionEnabled(false)
                .setPipelineStartedListener(new StartedEventListener())
                .setPipelineCompletedListener(new SuccessEventListener())
                .setPipelineFailedListener(new FailureEventListener())
                .build()
                .run();
    }

    private static Cluster connect() {
        return Cluster.connect("couchbase://localhost:11210",
                "Administrator",
                "password");
    }
}