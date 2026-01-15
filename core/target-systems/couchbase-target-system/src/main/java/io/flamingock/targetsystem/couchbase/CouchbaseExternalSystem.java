package io.flamingock.targetsystem.couchbase;

import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.Cluster;
import io.flamingock.api.external.ExternalSystem;

public interface CouchbaseExternalSystem extends ExternalSystem {
    Cluster getCluster();

    Bucket getBucket();

    String getBucketName();
}
