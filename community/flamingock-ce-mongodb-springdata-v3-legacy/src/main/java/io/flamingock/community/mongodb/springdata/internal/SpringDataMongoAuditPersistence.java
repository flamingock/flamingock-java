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
package io.flamingock.community.mongodb.springdata.internal;

import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.core.configuration.community.CommunityConfigurable;
import io.flamingock.internal.core.store.persistence.community.AbstractCommunityAuditPersistence;
import io.flamingock.internal.util.Result;
import io.flamingock.internal.util.id.RunnerId;
import io.flamingock.targetsystem.mongodb.springdata.MongoSpringDataTargetSystem;

import java.util.Map;

public class SpringDataMongoAuditPersistence extends AbstractCommunityAuditPersistence {

    private final MongoSpringDataTargetSystem targetSystem;
    private final String auditCollectionName;
    private SpringDataMongoAuditor auditor;


    public SpringDataMongoAuditPersistence(MongoSpringDataTargetSystem targetSystem,
                                           String auditCollectionName,
                                           CommunityConfigurable localConfiguration) {
        super(localConfiguration);
        this.targetSystem = targetSystem;
        this.auditCollectionName = auditCollectionName;
    }

    @Override
    protected void doInitialize(RunnerId runnerId) {
        auditor = new SpringDataMongoAuditor(targetSystem, auditCollectionName);
        auditor.initialize(targetSystem.isAutoCreate());
    }

    @Override
    public Map<String, AuditEntry> getAuditSnapshotByChangeId() {
        return auditor.getAuditSnapshotByChangeId();
    }

    @Override
    public Result writeEntry(AuditEntry auditEntry) {
        return auditor.writeEntry(auditEntry);
    }
}
