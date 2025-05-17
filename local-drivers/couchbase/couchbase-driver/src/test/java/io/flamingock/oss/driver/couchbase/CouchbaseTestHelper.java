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

package io.flamingock.oss.driver.couchbase;

import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.Collection;
import com.couchbase.client.java.json.JsonObject;
import com.couchbase.client.java.query.QueryOptions;
import com.couchbase.client.java.query.QueryResult;
import com.couchbase.client.java.query.QueryScanConsistency;
import io.flamingock.internal.core.engine.audit.writer.AuditEntry;
import io.flamingock.oss.driver.couchbase.internal.util.CouchBaseUtil;
import io.flamingock.oss.driver.couchbase.internal.util.N1QLQueryProvider;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static io.flamingock.oss.driver.couchbase.internal.CouchbaseConstants.DOCUMENT_TYPE_AUDIT_ENTRY;

public class CouchbaseTestHelper {

    private final Cluster cluster;

    public CouchbaseTestHelper(Cluster cluster) {
        this.cluster = cluster;
    }

    public List<AuditEntry> getAuditEntriesSorted(Collection collection) {
        QueryResult result = cluster.query(
                N1QLQueryProvider.selectAllChangesQuery(collection.bucketName(), collection.scopeName(),
                        collection.name()),
                QueryOptions.queryOptions().parameters(JsonObject.create().put("p", DOCUMENT_TYPE_AUDIT_ENTRY))
                        .scanConsistency(QueryScanConsistency.REQUEST_PLUS));
        return result
                .rowsAsObject()
                .stream()
                .map(CouchBaseUtil::auditEntryFromEntity)
                .sorted(Comparator.comparing(AuditEntry::getCreatedAt))
                .collect(Collectors.toList());
    }

    public boolean indexExists(String bucketName, String indexName) {
        return cluster
                .queryIndexes()
                .getAllIndexes(bucketName)
                .stream()
                .filter(i -> i.name().equals(indexName))
                .count() == 1;
    }
}
