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

import io.flamingock.commons.utils.RunnerId;
import io.flamingock.commons.utils.TimeService;
import io.flamingock.commons.utils.http.Http;
import io.flamingock.commons.utils.id.EnvironmentId;
import io.flamingock.commons.utils.id.ServiceId;
import io.flamingock.core.api.exception.FlamingockException;
import io.flamingock.core.cloud.api.auth.AuthResponse;
import io.flamingock.core.cloud.audit.HtttpAuditWriter;
import io.flamingock.core.cloud.auth.AuthClient;
import io.flamingock.core.cloud.auth.AuthManager;
import io.flamingock.core.cloud.auth.HttpAuthClient;
import io.flamingock.core.cloud.lock.CloudLockService;
import io.flamingock.core.cloud.lock.client.HttpLockServiceClient;
import io.flamingock.core.cloud.lock.client.LockServiceClient;
import io.flamingock.core.cloud.planner.CloudExecutionPlanner;
import io.flamingock.core.cloud.planner.client.ExecutionPlannerClient;
import io.flamingock.core.cloud.planner.client.HttpExecutionPlannerClient;
import io.flamingock.core.cloud.transaction.CloudTransactioner;
import io.flamingock.core.configurator.cloud.CloudConfigurable;
import io.flamingock.core.configurator.core.CoreConfigurable;
import io.flamingock.core.engine.ConnectionEngine;
import io.flamingock.core.engine.audit.AuditWriter;
import io.flamingock.core.engine.execution.ExecutionPlanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Optional;

public final class CloudEngine implements ConnectionEngine {

    private static final Logger logger = LoggerFactory.getLogger(CloudEngine.class);

    private final EnvironmentId environmentId;

    private final ServiceId serviceId;

    private final CloudTransactioner cloudTransactioner;

    private final AuditWriter auditWriter;

    private final ExecutionPlanner executionPlanner;
    private final String jwt;

    public static Factory newFactory(RunnerId runnerId,
                                     CoreConfigurable coreConfiguration,
                                     CloudConfigurable cloudConfiguration,
                                     CloudTransactioner transactioner,
                                     Http.RequestBuilderFactory requestBuilderFactory) {
        return new Factory(runnerId, coreConfiguration, cloudConfiguration, transactioner, requestBuilderFactory);
    }

    private CloudEngine(EnvironmentId environmentId,
                        ServiceId serviceId,
                        String jwt,
                        AuditWriter auditWriter,
                        ExecutionPlanner executionPlanner,
                        CloudTransactioner cloudTransactioner) {
        this.environmentId =environmentId;
        this.serviceId = serviceId;
        this.jwt = jwt;
        this.auditWriter = auditWriter;
        this.executionPlanner = executionPlanner;
        this.cloudTransactioner = cloudTransactioner;
    }

    public EnvironmentId getEnvironmentId() {
        return environmentId;
    }

    public ServiceId getServiceId() {
        return serviceId;
    }

    public String getJwt() {
        return jwt;
    }

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

    public static class Factory {

        private final RunnerId runnerId;
        private final CoreConfigurable coreConfiguration;
        private final CloudConfigurable cloudConfiguration;
        private final CloudTransactioner transactioner;
        private final Http.RequestBuilderFactory requestBuilderFactory;

        private Factory(
                RunnerId runnerId,
                CoreConfigurable coreConfiguration,
                CloudConfigurable cloudConfiguration,
                CloudTransactioner transactioner,
                Http.RequestBuilderFactory requestBuilderFactory) {
            this.runnerId = runnerId;
            this.coreConfiguration = coreConfiguration;
            this.cloudConfiguration = cloudConfiguration;
            this.transactioner = transactioner;
            this.requestBuilderFactory = requestBuilderFactory;
        }

        public CloudEngine initializeAndGet() {
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
            String jwt = authResponse.getJwt();

            AuditWriter auditWriter = new HtttpAuditWriter(
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

            ExecutionPlanner executionPlanner = new CloudExecutionPlanner(
                    runnerId,
                    executionPlannerClient,
                    coreConfiguration,
                    new CloudLockService(lockClient),
                    transactioner,
                    TimeService.getDefault()
            );
            if (transactioner != null) {
                transactioner.initialize();
            }

            return new CloudEngine(
                    environmentId,
                    serviceId,
                    jwt,
                    auditWriter,
                    executionPlanner,
                    transactioner
            );
        }



        public Runnable getCloser() {
            return () -> {
                if (requestBuilderFactory != null) {
                    try {
                        requestBuilderFactory.close();
                    } catch (IOException ex) {
                        logger.warn("Error closing request builder factory", ex);
                    }
                }
                if (transactioner != null) {
                    try {
                        transactioner.close();
                    } catch (Exception ex) {
                        logger.warn("Error closing transactioner", ex);

                    }
                }
            };
        }
    }

}
