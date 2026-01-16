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
package io.flamingock.store.sql;

import io.flamingock.internal.common.core.context.ContextResolver;
import io.flamingock.internal.core.configuration.community.CommunityConfigurable;
import io.flamingock.internal.core.external.store.CommunityAuditStore;
import io.flamingock.internal.core.external.store.audit.community.CommunityAuditPersistence;
import io.flamingock.internal.core.external.store.lock.community.CommunityLockService;
import io.flamingock.internal.util.Constants;
import io.flamingock.internal.util.constants.CommunityPersistenceConstants;
import io.flamingock.internal.util.id.RunnerId;
import io.flamingock.store.sql.internal.SqlAuditPersistence;
import io.flamingock.store.sql.internal.SqlLockService;
import io.flamingock.externalsystem.sql.api.SqlExternalSystem;

import javax.sql.DataSource;

public class SqlAuditStore implements CommunityAuditStore {

    private final DataSource dataSource;
    private CommunityConfigurable communityConfiguration;
    private RunnerId runnerId;
    private SqlAuditPersistence persistence;
    private SqlLockService lockService;
    private String auditRepositoryName = CommunityPersistenceConstants.DEFAULT_AUDIT_STORE_NAME;
    private String lockRepositoryName = CommunityPersistenceConstants.DEFAULT_LOCK_STORE_NAME;
    private boolean autoCreate = true;

    private SqlAuditStore(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Creates a {@link SqlAuditStore} using the same SQL datasource
     * configured in the given {@link SqlExternalSystem}.
     * <p>
     * Only the underlying SQL datasource is reused.
     * No additional target-system configuration is carried over.
     *
     * @param targetSystem the target system from which to derive the datasource
     * @return a new audit store bound to the same SQL datasource as the target system
     */
    public static SqlAuditStore from(SqlExternalSystem targetSystem) {
        return new SqlAuditStore(targetSystem.getDataSource());
    }

    @Override
    public String getId() {
        return Constants.DEFAULT_SQL_AUDIT_STORE;
    }

    public SqlAuditStore withAuditRepositoryName(String auditRepositoryName) {
        this.auditRepositoryName = auditRepositoryName;
        return this;
    }

    public SqlAuditStore withLockRepositoryName(String lockRepositoryName) {
        this.lockRepositoryName = lockRepositoryName;
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
            lockService = new SqlLockService(dataSource, lockRepositoryName);
            lockService.initialize(autoCreate);
        }
        return lockService;
    }
}
