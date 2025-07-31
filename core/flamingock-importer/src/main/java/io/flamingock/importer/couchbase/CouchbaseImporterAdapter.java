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

package io.flamingock.importer.couchbase;

import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.query.QueryResult;
import io.flamingock.importer.ImporterAdapter;
import io.flamingock.internal.common.core.audit.AuditEntry;

import java.util.List;
import java.util.stream.Collectors;

public class CouchbaseImporterAdapter implements ImporterAdapter {

    private final Cluster cluster;
    private final String bucketName;

    public CouchbaseImporterAdapter(Cluster cluster, String bucketName) {
        this.cluster = cluster;
        this.bucketName = bucketName;
    }

    @Override
    public List<AuditEntry> getAuditEntries() {
        QueryResult result = cluster.query(
                "SELECT `" + bucketName + "`.* FROM `" + bucketName + "`"
        );

        return result.rowsAsObject().stream()
                .map(CouchbaseChangeEntry::fromJson)
                .map(CouchbaseChangeEntry::toAuditEntry)
                .collect(Collectors.toList());
    }
}