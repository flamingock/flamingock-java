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
package io.flamingock.cli.factory;

import com.couchbase.client.java.Cluster;
import io.flamingock.cli.config.DatabaseConfig;

public class CouchbaseClusterFactory {

    public static Cluster createCouchbaseCluster(DatabaseConfig.CouchbaseConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("Couchbase configuration is required");
        }

        if (config.getBucketName() == null) {
            throw new IllegalArgumentException("Bucket name is required");
        }

        if (config.getEndpoint() == null) {
            throw new IllegalArgumentException("Couchbase endpoint is required");
        }

        if (config.getUsername() == null) {
            throw new IllegalArgumentException("Couchbase username is required");
        }

        if (config.getPassword() == null) {
            throw new IllegalArgumentException("Couchbase password is required");
        }

        try {
            Cluster couchbaseCluster = Cluster.connect(config.getEndpoint(), config.getUsername(), config.getPassword());

            // Test the connection by listing collections in bucket
            couchbaseCluster.bucket(config.getBucketName()).collections();

            return couchbaseCluster;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create Couchbase cluster: " + e.getMessage(), e);
        }
    }
}
