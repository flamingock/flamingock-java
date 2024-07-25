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

package io.flamingock.core.cloud;

import io.flamingock.core.cloud.audit.HtttpAuditWriter;
import io.flamingock.core.cloud.auth.AuthClient;
import io.flamingock.core.cloud.auth.AuthManager;
import io.flamingock.core.cloud.auth.HttpAuthClient;
import io.flamingock.core.cloud.auth.AuthResponse;
import io.flamingock.core.cloud.transaction.CloudTransactioner;
import io.flamingock.core.configurator.cloud.CloudConfigurable;
import io.flamingock.core.configurator.core.CoreConfigurable;
import io.flamingock.core.configurator.core.EnvironmentId;
import io.flamingock.core.configurator.core.ServiceId;
import io.flamingock.core.engine.ConnectionEngine;
import io.flamingock.core.engine.audit.AuditWriter;
import io.flamingock.core.cloud.lock.CloudLockService;
import io.flamingock.core.cloud.lock.client.HttpLockServiceClient;
import io.flamingock.core.cloud.lock.client.LockServiceClient;
import io.flamingock.core.cloud.planner.CloudExecutionPlanner;
import io.flamingock.core.cloud.planner.client.ExecutionPlannerClient;
import io.flamingock.core.cloud.planner.client.HttpExecutionPlannerClient;
import io.flamingock.core.engine.execution.ExecutionPlanner;
import io.flamingock.core.runner.RunnerId;
import io.flamingock.core.util.TimeService;
import io.flamingock.core.util.http.Http;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Optional;

public class CloudConnectionEngine implements ConnectionEngine {
    private static final Logger logger = LoggerFactory.getLogger(CloudConnectionEngine.class);

    private final CoreConfigurable coreConfiguration;

    private final CloudConfigurable cloudConfiguration;

    private final Http.RequestBuilderFactory requestBuilderFactory;

    private final CloudTransactioner cloudTransactioner;

    private AuditWriter auditWriter;

    private ExecutionPlanner executionPlanner;


    public CloudConnectionEngine(CoreConfigurable coreConfiguration,
                                 CloudConfigurable cloudConfiguration,
                                 Http.RequestBuilderFactory requestBuilderFactory,
                                 CloudTransactioner cloudTransactioner) {
        this.coreConfiguration = coreConfiguration;
        this.cloudConfiguration = cloudConfiguration;
        this.requestBuilderFactory = requestBuilderFactory;
        this.cloudTransactioner = cloudTransactioner;
    }

    public AuditWriter getAuditWriter() {
        return auditWriter;
    }


    @Override
    public void initialize(RunnerId runnerId) {

        AuthClient authClient = new HttpAuthClient(
                cloudConfiguration.getHost(),
                cloudConfiguration.getApiVersion(),
                requestBuilderFactory);

        AuthManager authManager = new AuthManager(
                cloudConfiguration.getApiToken(),
                cloudConfiguration.getServiceName(),
                cloudConfiguration.getEnvironmentName(),
                authClient);
        AuthResponse authResponse = authManager.authenticate();

        EnvironmentId environmentId = EnvironmentId.fromString(authResponse.getEnvironmentId());
        ServiceId serviceId = ServiceId.fromString(authResponse.getServiceId());
        auditWriter = new HtttpAuditWriter(
                cloudConfiguration.getHost(),
                environmentId,
                serviceId,
                runnerId,
                cloudConfiguration.getApiVersion(),
                requestBuilderFactory,
                authManager
        );

        LockServiceClient lockClient = new HttpLockServiceClient(
                cloudConfiguration.getHost(),
                cloudConfiguration.getApiVersion(),
                requestBuilderFactory,
                authManager
        );

        ExecutionPlannerClient executionPlannerClient = new HttpExecutionPlannerClient(
                cloudConfiguration.getHost(),
                environmentId,
                serviceId,
                runnerId,
                cloudConfiguration.getApiVersion(),
                requestBuilderFactory,
                authManager
        );

        executionPlanner = new CloudExecutionPlanner(
                runnerId,
                executionPlannerClient,
                coreConfiguration,
                new CloudLockService(lockClient),
                cloudTransactioner,
                TimeService.getDefault()
        );
        getTransactionWrapper().ifPresent(CloudTransactioner::initialize);


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
    public void close() {
        if (requestBuilderFactory != null) {
            try {
                requestBuilderFactory.close();
            } catch (IOException ex) {
              logger.warn("Error closing request builder factory", ex);
            }
        }
        getTransactionWrapper().ifPresent(CloudTransactioner::close);
    }
}
