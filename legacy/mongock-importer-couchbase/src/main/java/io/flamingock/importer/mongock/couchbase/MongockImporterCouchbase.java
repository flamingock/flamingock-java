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
package io.flamingock.importer.mongock.couchbase;

import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.json.JsonObject;
import com.couchbase.client.java.query.QueryOptions;
import com.couchbase.client.java.query.QueryResult;
import com.couchbase.client.java.query.QueryScanConsistency;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.audit.AuditHistoryReader;

import java.util.List;
import java.util.stream.Collectors;

public class MongockImporterCouchbase implements AuditHistoryReader {

    private static final String MONGOCK_CHANGE_ENTRY_DOCTYPE = "mongockChangeEntry";

    private final Cluster cluster;
    private final String bucketName;
    private final String scopeName;
    private final String collectionName;

    public MongockImporterCouchbase(Cluster cluster, String bucketName, String scopeName, String collectionName) {
        this.cluster = cluster;
        this.bucketName = bucketName;
        this.scopeName = scopeName;
        this.collectionName = collectionName;
    }

    @Override
    public List<AuditEntry> getAuditHistory() {
        QueryResult result = cluster.query(
                String.format(
                        "SELECT `%s`.* FROM `%s`.`%s`.`%s` WHERE `_doctype` = $p1",
                        collectionName, bucketName, scopeName, collectionName
                ),
                QueryOptions.queryOptions()
                        .scanConsistency(QueryScanConsistency.REQUEST_PLUS)
                        .parameters(JsonObject.create().put("p1", MONGOCK_CHANGE_ENTRY_DOCTYPE))
        );

        return result.rowsAsObject().stream()
                .map(CouchbaseChangeEntry::fromJson)
                .map(CouchbaseChangeEntry::toAuditEntry)
                .collect(Collectors.toList());
    }
}