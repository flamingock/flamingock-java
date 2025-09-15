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
package io.flamingock.community.mongodb.sync.internal;

import com.mongodb.client.ClientSession;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.core.configuration.community.CommunityConfigurable;
import io.flamingock.internal.core.store.audit.community.AbstractCommunityAuditPersistence;
import io.flamingock.internal.util.Result;
import io.flamingock.internal.util.id.RunnerId;
import io.flamingock.targetystem.mongodb.sync.MongoSyncTargetSystem;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MongoSyncAuditPersistence extends AbstractCommunityAuditPersistence {

    private MongoSyncAuditor auditor;
    private MongoSyncTargetSystem targetSystem;
    private final String auditCollectionName;


    public MongoSyncAuditPersistence(MongoSyncTargetSystem targetSystem,
                                     String auditCollectionName,
                                     CommunityConfigurable localConfiguration) {
        super(localConfiguration);
        this.targetSystem = targetSystem;
        this.auditCollectionName = auditCollectionName;
    }

    @Override
    protected void doInitialize(RunnerId runnerId) {


        //Auditor
        auditor = new MongoSyncAuditor(targetSystem, auditCollectionName);
        auditor.initialize(targetSystem.isAutoCreate());

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
