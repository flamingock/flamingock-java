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
package io.flamingock.store.mongodb.sync.internal;

import com.mongodb.ReadConcern;
import com.mongodb.ReadPreference;
import com.mongodb.WriteConcern;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoDatabase;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.core.configuration.community.CommunityConfigurable;
import io.flamingock.internal.core.external.store.audit.community.AbstractCommunityAuditPersistence;
import io.flamingock.internal.util.Result;
import io.flamingock.internal.util.id.RunnerId;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MongoDBSyncAuditPersistence extends AbstractCommunityAuditPersistence {

    private MongoDBSyncAuditor auditor;
    private final MongoDatabase database;
    private final String auditCollectionName;
    private final ReadConcern readConcern;
    private final ReadPreference readPreference;
    private final WriteConcern writeConcern;
    private final boolean autoCreate;


    public MongoDBSyncAuditPersistence(CommunityConfigurable localConfiguration,
                                     MongoDatabase database,
                                     String auditCollectionName,
                                     ReadConcern readConcern,
                                     ReadPreference readPreference,
                                     WriteConcern writeConcern,
                                     boolean autoCreate) {
        super(localConfiguration);
        this.database = database;
        this.auditCollectionName = auditCollectionName;
        this.readConcern = readConcern;
        this.readPreference = readPreference;
        this.writeConcern = writeConcern;
        this.autoCreate = autoCreate;
    }

    @Override
    protected void doInitialize(RunnerId runnerId) {
        //Auditor
        auditor = new MongoDBSyncAuditor(database, auditCollectionName, readConcern, readPreference, writeConcern);
        auditor.initialize(autoCreate);
    }

    @Deprecated
    @Override
    public Set<Class<?>> getNonGuardedTypes() {
        return new HashSet<>(Collections.singletonList(ClientSession.class));
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
