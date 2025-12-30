/*
 * Copyright 2025 Flamingock (https://www.flamingock.io)
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
package io.flamingock.store.sql.internal;

import io.flamingock.internal.core.configuration.community.CommunityConfigurable;
import io.flamingock.internal.core.store.audit.community.AbstractCommunityAuditPersistence;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.util.Result;
import io.flamingock.internal.util.id.RunnerId;

import javax.sql.DataSource;
import java.util.List;

public class SqlAuditPersistence extends AbstractCommunityAuditPersistence {

    private final DataSource dataSource;
    private final String auditRepositoryName;
    private final boolean autoCreate;
    private SqlAuditor auditor;

    public SqlAuditPersistence(CommunityConfigurable localConfiguration,
                               DataSource dataSource,
                               String auditRepositoryName,
                               boolean autoCreate) {
        super(localConfiguration);
        this.dataSource = dataSource;
        this.auditRepositoryName = auditRepositoryName;
        this.autoCreate = autoCreate;
    }

    @Override
    protected void doInitialize(RunnerId runnerId) {
        auditor = new SqlAuditor(dataSource, auditRepositoryName, autoCreate);
        auditor.initialize();
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
