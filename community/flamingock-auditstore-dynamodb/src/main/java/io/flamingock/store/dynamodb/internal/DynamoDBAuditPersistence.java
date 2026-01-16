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
package io.flamingock.store.dynamodb.internal;

import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.core.configuration.community.CommunityConfigurable;
import io.flamingock.internal.core.external.store.audit.community.AbstractCommunityAuditPersistence;
import io.flamingock.internal.util.Result;
import io.flamingock.internal.util.id.RunnerId;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.util.List;

public class DynamoDBAuditPersistence extends AbstractCommunityAuditPersistence {

    private final DynamoDbClient client;
    private final String auditTableName;
    private final long readCapacityUnits;
    private final long writeCapacityUnits;
    private final boolean autoCreate;

    private DynamoDBAuditor auditor;

    public DynamoDBAuditPersistence(DynamoDbClient client,
                                    String auditTableName,
                                    long readCapacityUnits,
                                    long writeCapacityUnits,
                                    boolean autoCreate,
                                    CommunityConfigurable localConfiguration) {
        super(localConfiguration);
        this.client = client;
        this.auditTableName = auditTableName;
        this.readCapacityUnits = readCapacityUnits;
        this.writeCapacityUnits = writeCapacityUnits;
        this.autoCreate = autoCreate;
    }

    @Override
    protected void doInitialize(RunnerId runnerId) {
        auditor = new DynamoDBAuditor(client);
        auditor.initialize(
                autoCreate,
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
