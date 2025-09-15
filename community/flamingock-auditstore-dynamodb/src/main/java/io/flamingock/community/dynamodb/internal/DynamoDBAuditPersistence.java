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
package io.flamingock.community.dynamodb.internal;

import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.core.configuration.community.CommunityConfigurable;
import io.flamingock.internal.core.store.audit.community.AbstractCommunityAuditPersistence;
import io.flamingock.internal.util.Result;
import io.flamingock.internal.util.id.RunnerId;
import io.flamingock.targetsystem.dynamodb.DynamoDBTargetSystem;

import java.util.List;

public class DynamoDBAuditPersistence extends AbstractCommunityAuditPersistence {

    private final DynamoDBTargetSystem targetSystem;
    private final String auditTableName;
    private final long readCapacityUnits;
    private final long writeCapacityUnits;

    private DynamoDBAuditor auditor;


    public DynamoDBAuditPersistence(DynamoDBTargetSystem targetSystem,
                                    String auditTableName,
                                    long readCapacityUnits,
                                    long writeCapacityUnits,
                                    CommunityConfigurable localConfiguration) {
        super(localConfiguration);
        this.targetSystem = targetSystem;
        this.auditTableName = auditTableName;
        this.readCapacityUnits = readCapacityUnits;
        this.writeCapacityUnits = writeCapacityUnits;
    }

    @Override
    protected void doInitialize(RunnerId runnerId) {
        auditor = new DynamoDBAuditor(targetSystem);
        auditor.initialize(
                targetSystem.isAutoCreate(),
                auditTableName,
                readCapacityUnits,
                writeCapacityUnits);
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
