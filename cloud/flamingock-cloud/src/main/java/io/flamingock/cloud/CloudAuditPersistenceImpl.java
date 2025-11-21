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
package io.flamingock.cloud;

import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.audit.AuditSnapshotBuilder;
import io.flamingock.internal.common.core.audit.issue.AuditEntryIssue;
import io.flamingock.internal.common.core.context.ContextContributor;
import io.flamingock.internal.util.Result;
import io.flamingock.internal.util.id.EnvironmentId;
import io.flamingock.internal.util.id.JwtProperty;
import io.flamingock.internal.util.id.ServiceId;
import io.flamingock.internal.core.store.audit.cloud.CloudAuditPersistence;
import io.flamingock.internal.common.core.context.ContextInjectable;
import io.flamingock.internal.core.store.audit.LifecycleAuditWriter;
import io.flamingock.internal.core.plan.ExecutionPlanner;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public final class CloudAuditPersistenceImpl implements CloudAuditPersistence, ContextContributor {

    private final EnvironmentId environmentId;

    private final ServiceId serviceId;

    private final LifecycleAuditWriter auditWriter;

    private final ExecutionPlanner executionPlanner;
    private final String jwt;

    CloudAuditPersistenceImpl(EnvironmentId environmentId,
                              ServiceId serviceId,
                              String jwt,
                              LifecycleAuditWriter auditWriter,
                              ExecutionPlanner executionPlanner,
                              Runnable closer) {
        this.environmentId =environmentId;
        this.serviceId = serviceId;
        this.jwt = jwt;
        this.auditWriter = auditWriter;
        this.executionPlanner = executionPlanner;
    }

    @Override
    public EnvironmentId getEnvironmentId() {
        return environmentId;
    }

    @Override
    public ServiceId getServiceId() {
        return serviceId;
    }

    @Override
    public String getJwt() {
        return jwt;
    }

    @Override
    public ExecutionPlanner getExecutionPlanner() {
        return executionPlanner;
    }

    @Override
    public void contributeToContext(ContextInjectable contextInjectable) {
        contextInjectable.setProperty(JwtProperty.fromString(jwt));
        contextInjectable.setProperty(environmentId);
        contextInjectable.setProperty(serviceId);
    }

    @Override
    public Result writeEntry(AuditEntry auditEntry) {
        return auditWriter.writeEntry(auditEntry);
    }

    @Override
    public List<AuditEntry> getAuditHistory() {
        throw new UnsupportedOperationException("getAuditHistory still not implemented for cloud edition");
    }

    @Override
    public List<AuditEntry> getAuditSnapshot() {
        throw new UnsupportedOperationException("getSnapshotList still not implemented for cloud edition");
    }

    @Override
    public List<AuditEntryIssue> getAuditIssues() {
        throw new UnsupportedOperationException("getAuditIssues still not implemented for cloud edition");
    }

    @Override
    public Optional<AuditEntryIssue> getAuditIssueByChangeId(String changeId) {
        throw new UnsupportedOperationException("getAuditIssueByChangeId still not implemented for cloud edition");
    }
}
