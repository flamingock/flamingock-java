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

package io.flamingock.community.couchbase.internal;

import com.couchbase.client.core.error.CouchbaseException;
import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.Collection;
import com.couchbase.client.java.json.JsonObject;
import com.couchbase.client.java.kv.PersistTo;
import com.couchbase.client.java.kv.ReplicateTo;
import com.couchbase.client.java.kv.UpsertOptions;
import com.couchbase.client.java.query.QueryOptions;
import com.couchbase.client.java.query.QueryResult;
import com.couchbase.client.java.query.QueryScanConsistency;
import io.flamingock.internal.core.community.LocalAuditor;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.core.engine.audit.domain.AuditStageStatus;
import io.flamingock.internal.util.Result;
import io.flamingock.internal.util.TimeUtil;
import io.flamingock.community.couchbase.internal.util.CouchBaseUtil;
import io.flamingock.community.couchbase.internal.util.AuditEntryKeyGenerator;
import io.flamingock.community.couchbase.internal.util.N1QLQueryProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static io.flamingock.internal.core.community.Constants.KEY_AUTHOR;
import static io.flamingock.internal.core.community.Constants.KEY_CHANGEUNIT_CLASS;
import static io.flamingock.internal.core.community.Constants.KEY_INVOKED_METHOD;
import static io.flamingock.internal.core.community.Constants.KEY_CHANGE_ID;
import static io.flamingock.internal.core.community.Constants.KEY_ERROR_TRACE;
import static io.flamingock.internal.core.community.Constants.KEY_EXECUTION_HOSTNAME;
import static io.flamingock.internal.core.community.Constants.KEY_EXECUTION_ID;
import static io.flamingock.internal.core.community.Constants.KEY_EXECUTION_MILLIS;
import static io.flamingock.internal.core.community.Constants.KEY_METADATA;
import static io.flamingock.internal.core.community.Constants.KEY_STATE;
import static io.flamingock.internal.core.community.Constants.KEY_SYSTEM_CHANGE;
import static io.flamingock.internal.core.community.Constants.KEY_TIMESTAMP;
import static io.flamingock.internal.core.community.Constants.KEY_TYPE;
import static io.flamingock.community.couchbase.internal.CouchbaseConstants.DOCUMENT_TYPE_AUDIT_ENTRY;
import static io.flamingock.community.couchbase.internal.CouchbaseConstants.DOCUMENT_TYPE_KEY;

public class CouchbaseAuditor implements LocalAuditor {

    private static final Logger logger = LoggerFactory.getLogger(CouchbaseAuditor.class);

    private static final Set<String> QUERY_FIELDS = new LinkedHashSet<>();

    static {
        QUERY_FIELDS.add(DOCUMENT_TYPE_KEY);
        QUERY_FIELDS.add(KEY_AUTHOR);
        QUERY_FIELDS.add(KEY_CHANGE_ID);
        QUERY_FIELDS.add(KEY_EXECUTION_ID);
    }

    protected final Collection collection;
    protected final Cluster cluster;
    protected final CouchbaseGenericRepository couchbaseGenericRepository;

    private final AuditEntryKeyGenerator keyGenerator = new AuditEntryKeyGenerator();

    protected CouchbaseAuditor(Cluster cluster, Collection collection) {
        this.cluster = cluster;
        this.collection = collection;
        this.couchbaseGenericRepository = new CouchbaseGenericRepository(cluster, collection, QUERY_FIELDS);
    }

    protected void initialize(boolean indexCreation) {
        this.couchbaseGenericRepository.initialize(indexCreation);
    }

    /**
     * Only for testing
     */
    public void deleteAll() {
        this.couchbaseGenericRepository.deleteAll();
    }

    @Override
    public Result writeEntry(AuditEntry auditEntry) {

        String key = keyGenerator.toKey(auditEntry);
        logger.debug("Saving audit entry with key {}", key);
        try {
            collection.upsert(key, toEntity(auditEntry),
                    UpsertOptions.upsertOptions().durability(PersistTo.ACTIVE, ReplicateTo.NONE));
        } catch (CouchbaseException couchbaseException) {
            logger.warn("Error saving audit entry with key {}", key, couchbaseException);
            throw new RuntimeException(couchbaseException);
        }

        return Result.OK();
    }

    private JsonObject toEntity(AuditEntry auditEntry) {
        JsonObject document = JsonObject.create();
        this.couchbaseGenericRepository.addField(document, KEY_EXECUTION_ID, auditEntry.getExecutionId());
        this.couchbaseGenericRepository.addField(document, KEY_CHANGE_ID, auditEntry.getTaskId());
        this.couchbaseGenericRepository.addField(document, KEY_AUTHOR, auditEntry.getAuthor());
        this.couchbaseGenericRepository.addField(document, KEY_TIMESTAMP, TimeUtil.toDate(auditEntry.getCreatedAt()));
        this.couchbaseGenericRepository.addField(document, KEY_STATE, auditEntry.getState().name());
        this.couchbaseGenericRepository.addField(document, KEY_TYPE, auditEntry.getType().name());
        this.couchbaseGenericRepository.addField(document, KEY_CHANGEUNIT_CLASS, auditEntry.getClassName());
        this.couchbaseGenericRepository.addField(document, KEY_INVOKED_METHOD, auditEntry.getMethodName());
        this.couchbaseGenericRepository.addField(document, KEY_METADATA, auditEntry.getMetadata());
        this.couchbaseGenericRepository.addField(document, KEY_EXECUTION_MILLIS, auditEntry.getExecutionMillis());
        this.couchbaseGenericRepository.addField(document, KEY_EXECUTION_HOSTNAME, auditEntry.getExecutionHostname());
        this.couchbaseGenericRepository.addField(document, KEY_ERROR_TRACE, auditEntry.getErrorTrace());
        this.couchbaseGenericRepository.addField(document, KEY_SYSTEM_CHANGE, auditEntry.getSystemChange());
        this.couchbaseGenericRepository.addField(document, DOCUMENT_TYPE_KEY, DOCUMENT_TYPE_AUDIT_ENTRY);
        return document;
    }

    @Override
    public AuditStageStatus getAuditStageStatus() {
        AuditStageStatus.EntryBuilder builder = AuditStageStatus.entryBuilder();
        QueryResult result = cluster.query(
                N1QLQueryProvider.selectAllChangesQuery(collection.bucketName(), collection.scopeName(),
                        collection.name()),
                QueryOptions.queryOptions().parameters(JsonObject.create().put("p", DOCUMENT_TYPE_AUDIT_ENTRY))
                        .scanConsistency(QueryScanConsistency.REQUEST_PLUS));
        result
                .rowsAsObject()
                .stream()
                .map(CouchBaseUtil::auditEntryFromEntity)
                .collect(Collectors.toList())
                .forEach(builder::addEntry);
        return builder.build();
    }


}
