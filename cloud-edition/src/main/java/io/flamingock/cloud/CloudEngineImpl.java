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

import io.flamingock.commons.utils.id.EnvironmentId;
import io.flamingock.commons.utils.id.JwtProperty;
import io.flamingock.commons.utils.id.ServiceId;
import io.flamingock.core.cloud.transaction.CloudTransactioner;
import io.flamingock.core.cloud.CloudEngine;
import io.flamingock.core.context.ContextInjectable;
import io.flamingock.core.engine.audit.AuditWriter;
import io.flamingock.core.engine.execution.ExecutionPlanner;
import io.flamingock.core.system.SystemModuleManager;

import java.util.Optional;

public final class CloudEngineImpl implements CloudEngine {

    private final EnvironmentId environmentId;

    private final ServiceId serviceId;

    private final CloudTransactioner cloudTransactioner;

    private final AuditWriter auditWriter;

    private final ExecutionPlanner executionPlanner;
    private final String jwt;

    CloudEngineImpl(EnvironmentId environmentId,
                    ServiceId serviceId,
                    String jwt,
                    AuditWriter auditWriter,
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
    public AuditWriter getAuditWriter() {
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

    @Override
    public void contributeToSystemModules(SystemModuleManager systemModuleManager) {
        //TODO it will need to inject the SystemModule for Mongock importer
    }
}
