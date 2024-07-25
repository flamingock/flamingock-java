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

package io.flamingock.oss.driver.couchbase.internal.util;

import com.couchbase.client.core.deps.com.fasterxml.jackson.databind.JsonNode;
import com.couchbase.client.core.json.Mapper;
import com.couchbase.client.core.manager.CoreBucketManager;
import com.couchbase.client.java.AsyncUtils;
import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.manager.bucket.GetBucketOptions;

public final class CouchbaseVersionUtil {
  
  private static final String UNKNOWN_VERSION = "0.unknown";
  
  private CouchbaseVersionUtil() {
  }

  /**
   * Return the couchbase server version.
   * @param bucket The bucket for which the server node version is requested.
   * @return the couchbase server version.
   */
  public static String getCouchbaseServerVersion(Bucket bucket) {
    CoreBucketManager coreBucketManager = new CoreBucketManager(bucket.core());
    return AsyncUtils.block(
            coreBucketManager.getBucket(bucket.name(), GetBucketOptions.getBucketOptions().build())
                .thenApply(CouchbaseVersionUtil::parseBucketSettings)
        );
  }

  /**
   * Returns <code>true</code> if the couchbase server version is 7 or higher.
   * @param bucket The bucket for which the server node version should be checked. 
   * @return <code>true</code> if the couchbase server version is 7 or higher, otherwise <code>false</code>.
   */
  public static boolean is7andUp(Bucket bucket){
    String version = getCouchbaseServerVersion(bucket);
    String major = version.split("\\.")[0];
    return Integer.parseInt(major) >= 7;
  }

  private static String parseBucketSettings(byte[] bucketBytes) {
    JsonNode tree = Mapper.decodeIntoTree(bucketBytes);
    JsonNode nodes = tree.get("nodes");
    if (nodes.isArray() && !nodes.isEmpty()) {
      for (final JsonNode node : nodes) {
        if(node.has("version")){
          return node.get("version").textValue();
        }
      }
    }
    // should never happen
    return UNKNOWN_VERSION;
  }
  
}
