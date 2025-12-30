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
package io.flamingock.store.couchbase;

import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.Collection;
import com.couchbase.client.java.json.JsonObject;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.couchbase.CouchbaseAuditMapper;
import io.flamingock.internal.common.couchbase.CouchbaseCollectionHelper;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class CouchbaseTestHelper {

    private final Cluster cluster;
    private final CouchbaseAuditMapper auditMapper = new CouchbaseAuditMapper();

    public CouchbaseTestHelper(Cluster cluster) {
        this.cluster = cluster;
    }

    public List<AuditEntry> getAuditEntriesSorted(Collection collection) {
        List<JsonObject> documents = CouchbaseCollectionHelper.selectAllDocuments(cluster, collection.bucketName(), collection.scopeName(), collection.name());
        return documents
                .stream()
                .map(auditMapper::fromDocument)
                .sorted()
                .collect(Collectors.toList());
    }
}
