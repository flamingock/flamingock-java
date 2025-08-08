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

import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.query.QueryOptions;
import com.couchbase.client.java.query.QueryResult;
import com.couchbase.client.java.query.QueryScanConsistency;
import io.flamingock.importer.ImporterAdapter;
import io.flamingock.internal.common.core.audit.AuditEntry;

import java.util.List;
import java.util.stream.Collectors;

public class CouchbaseImporterAdapter implements ImporterAdapter {

    private final Cluster cluster;
    private final String bucketName;
    private final String scopeName;
    private final String collectionName;

    public CouchbaseImporterAdapter(Cluster cluster, String bucketName, String scopeName, String collectionName) {
        this.cluster = cluster;
        this.bucketName = bucketName;
        this.scopeName = scopeName;
        this.collectionName = collectionName;
    }

    @Override
    public List<AuditEntry> getAuditEntries() {
        QueryResult result = cluster.query(
                String.format("SELECT `%s`.* FROM `%s`.`%s`.`%s`", collectionName, bucketName, scopeName, collectionName),
                QueryOptions.queryOptions().scanConsistency(QueryScanConsistency.REQUEST_PLUS)
        );

        return result.rowsAsObject().stream()
                .filter(d -> d.getString("_doctype") != null && d.getString("_doctype").equals("mongockChangeEntry"))
                .map(CouchbaseChangeEntry::fromJson)
                .map(CouchbaseChangeEntry::toAuditEntry)
                .collect(Collectors.toList());
    }
}