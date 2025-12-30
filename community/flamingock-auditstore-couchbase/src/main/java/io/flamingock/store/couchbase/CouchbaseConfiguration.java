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
package io.flamingock.store.couchbase;

import com.couchbase.client.core.io.CollectionIdentifier;
import io.flamingock.internal.util.constants.CommunityPersistenceConstants;
import io.flamingock.internal.core.store.audit.community.CommunityAuditStoreConfigurable;
import io.flamingock.internal.common.core.context.ContextResolver;

public class CouchbaseConfiguration implements CommunityAuditStoreConfigurable {

    private boolean autoCreate = true;
    private String scopeName = CollectionIdentifier.DEFAULT_SCOPE;
    private String auditRepositoryName = CommunityPersistenceConstants.DEFAULT_AUDIT_STORE_NAME;
    private String lockRepositoryName = CommunityPersistenceConstants.DEFAULT_LOCK_STORE_NAME;

    public boolean isAutoCreate() {
        return autoCreate;
    }

    public void setAutoCreate(boolean autoCreate) {
        this.autoCreate = autoCreate;
    }

    public String getScopeName() {
        return scopeName;
    }

    public void setScopeName(String scopeName) {
        this.scopeName = scopeName;
    }

    public String getAuditRepositoryName() {
        return auditRepositoryName;
    }

    public void setAuditRepositoryName(String auditRepositoryName) {
        this.auditRepositoryName = auditRepositoryName;
    }

    public String getLockRepositoryName() {
        return lockRepositoryName;
    }

    public void setLockRepositoryName(String lockRepositoryName) {
        this.lockRepositoryName = lockRepositoryName;
    }

    public void mergeConfig(ContextResolver dependencyContext) {
        dependencyContext.getPropertyAs("couchbase.autoCreate", Boolean.class)
                .ifPresent(this::setAutoCreate);
        dependencyContext.getPropertyAs("couchbase.scopeName", String.class)
                .ifPresent(this::setScopeName);
        dependencyContext.getPropertyAs("couchbase.auditRepositoryName", String.class)
                .ifPresent(this::setAuditRepositoryName);
        dependencyContext.getPropertyAs("couchbase.lockRepositoryName", String.class)
                .ifPresent(this::setLockRepositoryName);
    }
}
