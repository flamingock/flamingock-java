package io.flamingock.externalsystem.couchbase.api;

import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.Cluster;
import io.flamingock.api.external.ExternalSystem;

public interface CouchbaseExternalSystem extends ExternalSystem {
    Cluster getCluster();

    Bucket getBucket();

    String getBucketName();
}
