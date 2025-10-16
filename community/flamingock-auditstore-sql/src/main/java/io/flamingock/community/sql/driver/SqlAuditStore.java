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
package io.flamingock.community.sql.driver;

import io.flamingock.internal.core.store.CommunityAuditStore;
import io.flamingock.internal.core.store.audit.community.CommunityAuditPersistence;
import io.flamingock.internal.core.store.lock.community.CommunityLockService;
import io.flamingock.internal.common.core.context.ContextResolver;
import io.flamingock.internal.core.configuration.community.CommunityConfigurable;
import io.flamingock.internal.util.constants.CommunityPersistenceConstants;
import io.flamingock.internal.util.id.RunnerId;
import io.flamingock.community.sql.internal.SqlAuditPersistence;
import io.flamingock.community.sql.internal.SqlLockService;

import javax.sql.DataSource;

public class SqlAuditStore implements CommunityAuditStore {

    private final DataSource dataSource;
    private CommunityConfigurable communityConfiguration;
    private RunnerId runnerId;
    private SqlAuditPersistence persistence;
    private SqlLockService lockService;
    private String auditRepositoryName = CommunityPersistenceConstants.DEFAULT_AUDIT_STORE_NAME;
    private boolean autoCreate = true;

    public SqlAuditStore(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public SqlAuditStore withAuditRepositoryName(String auditRepositoryName) {
        this.auditRepositoryName = auditRepositoryName;
        return this;
    }

    public SqlAuditStore withAutoCreate(boolean autoCreate) {
        this.autoCreate = autoCreate;
        return this;
    }

    @Override
    public void initialize(ContextResolver baseContext) {
        runnerId = baseContext.getRequiredDependencyValue(RunnerId.class);
        communityConfiguration = baseContext.getRequiredDependencyValue(CommunityConfigurable.class);
    }

    @Override
    public synchronized CommunityAuditPersistence getPersistence() {
        if (persistence == null) {
            persistence = new SqlAuditPersistence(communityConfiguration, dataSource, auditRepositoryName, autoCreate);
            persistence.initialize(runnerId);
        }
        return persistence;
    }


    @Override
    public synchronized CommunityLockService getLockService() {
        if (lockService == null) {
            lockService = new SqlLockService(dataSource);
        }
        return lockService;
    }
}
