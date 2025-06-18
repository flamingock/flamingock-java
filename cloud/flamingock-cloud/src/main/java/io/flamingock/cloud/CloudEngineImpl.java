/*
 * Copyright 2023 Flamingock ("https://oss.flamingock.io")
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.flamingock.cloud;

import io.flamingock.internal.util.id.EnvironmentId;
import io.flamingock.internal.util.id.JwtProperty;
import io.flamingock.internal.util.id.ServiceId;
import io.flamingock.internal.core.cloud.transaction.CloudTransactioner;
import io.flamingock.internal.core.cloud.CloudEngine;
import io.flamingock.internal.common.core.context.ContextInjectable;
import io.flamingock.internal.core.engine.audit.ExecutionAuditWriter;
import io.flamingock.internal.core.engine.execution.ExecutionPlanner;

import java.util.Optional;

public final class CloudEngineImpl implements CloudEngine {

    private final EnvironmentId environmentId;

    private final ServiceId serviceId;

    private final CloudTransactioner cloudTransactioner;

    private final ExecutionAuditWriter auditWriter;

    private final ExecutionPlanner executionPlanner;
    private final String jwt;

    CloudEngineImpl(EnvironmentId environmentId,
                    ServiceId serviceId,
                    String jwt,
                    ExecutionAuditWriter auditWriter,
                    ExecutionPlanner executionPlanner,
                    CloudTransactioner cloudTransactioner,
                    Runnable closer) {
        this.environmentId =environmentId;
        this.serviceId = serviceId;
        this.jwt = jwt;
        this.auditWriter = auditWriter;
        this.executionPlanner = executionPlanner;
        this.cloudTransactioner = cloudTransactioner;
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
    public ExecutionAuditWriter getAuditWriter() {
        return auditWriter;
    }

    @Override
    public ExecutionPlanner getExecutionPlanner() {
        return executionPlanner;
    }

    @Override
    public Optional<CloudTransactioner> getTransactionWrapper() {
        return Optional.ofNullable(cloudTransactioner);
    }

    @Override
    public void contributeToContext(ContextInjectable contextInjectable) {
        CloudEngine.super.contributeToContext(contextInjectable);
        contextInjectable.setProperty(JwtProperty.fromString(jwt));
        contextInjectable.setProperty(environmentId);
        contextInjectable.setProperty(serviceId);
    }

}
