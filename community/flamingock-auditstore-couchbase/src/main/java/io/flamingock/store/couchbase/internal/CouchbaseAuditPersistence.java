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
package io.flamingock.store.couchbase.internal;

import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.Cluster;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.core.configuration.community.CommunityConfigurable;
import io.flamingock.internal.core.external.store.audit.community.AbstractCommunityAuditPersistence;
import io.flamingock.internal.util.Result;
import io.flamingock.internal.util.id.RunnerId;

import java.util.List;

public class CouchbaseAuditPersistence extends AbstractCommunityAuditPersistence {

    private final Cluster cluster;
    private final Bucket bucket;
    private final String scopeName;
    private final String auditRepositoryName;
    private final boolean autoCreate;

    private CouchbaseAuditor auditor;


    public CouchbaseAuditPersistence(CommunityConfigurable localConfiguration,
                                     Cluster cluster,
                                     Bucket bucket,
                                     String scopeName,
                                     String auditRepositoryName,
                                     boolean autoCreate) {
        super(localConfiguration);
        this.cluster = cluster;
        this.bucket = bucket;
        this.scopeName = scopeName;
        this.auditRepositoryName = auditRepositoryName;
        this.autoCreate = autoCreate;
    }

    @Override
    protected void doInitialize(RunnerId runnerId) {
        auditor = new CouchbaseAuditor(cluster, bucket);
        auditor.initialize(autoCreate, scopeName, auditRepositoryName);
    }

    @Override
    public List<AuditEntry> getAuditHistory() {
        return auditor.getAuditHistory();
    }

    @Override
    public Result writeEntry(AuditEntry auditEntry) {
        return auditor.writeEntry(auditEntry);
    }
}
