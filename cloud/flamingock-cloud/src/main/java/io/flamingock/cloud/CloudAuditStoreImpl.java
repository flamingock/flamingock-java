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
package io.flamingock.cloud;

import io.flamingock.internal.util.Constants;
import io.flamingock.internal.util.JsonObjectMapper;
import io.flamingock.internal.util.id.RunnerId;
import io.flamingock.internal.util.TimeService;
import io.flamingock.internal.util.http.Http;
import io.flamingock.internal.util.id.EnvironmentId;
import io.flamingock.internal.util.id.ServiceId;
import io.flamingock.internal.core.configuration.cloud.CloudConfigurable;
import io.flamingock.internal.core.configuration.core.CoreConfigurable;
import io.flamingock.internal.core.external.store.CloudAuditStore;
import io.flamingock.internal.common.cloud.auth.AuthResponse;
import io.flamingock.cloud.audit.HtttpAuditWriter;
import io.flamingock.cloud.auth.AuthManager;
import io.flamingock.cloud.auth.HttpAuthClient;
import io.flamingock.cloud.lock.CloudLockService;
import io.flamingock.cloud.lock.client.HttpLockServiceClient;
import io.flamingock.cloud.lock.client.LockServiceClient;
import io.flamingock.cloud.planner.CloudExecutionPlanner;
import io.flamingock.cloud.planner.client.ExecutionPlannerClient;
import io.flamingock.cloud.planner.client.HttpExecutionPlannerClient;
import io.flamingock.internal.core.external.store.audit.LifecycleAuditWriter;
import io.flamingock.internal.core.plan.ExecutionPlanner;
import io.flamingock.internal.common.core.context.ContextResolver;
import org.apache.http.impl.client.HttpClients;
import org.jetbrains.annotations.NotNull;
import io.flamingock.internal.util.log.FlamingockLoggerFactory;
import org.slf4j.Logger;

import java.io.IOException;

public class CloudAuditStoreImpl implements CloudAuditStore {
    private static final Logger logger = FlamingockLoggerFactory.getLogger("CloudAuditStore");
    private CloudAuditPersistenceImpl persistence;

    @Override
    public String getId() {
        return Constants.DEFAULT_CLOUD_AUDIT_STORE;
    }

    @Override
    public void initialize(ContextResolver baseContext) {
        RunnerId runnerId = baseContext.getRequiredDependencyValue(RunnerId.class);

        CoreConfigurable coreConfiguration = baseContext.getRequiredDependencyValue(CoreConfigurable.class);
        CloudConfigurable cloudConfiguration = baseContext.getRequiredDependencyValue(CloudConfigurable.class);

        Http.RequestBuilderFactory requestBuilderFactory =
                Http.builderFactory(HttpClients.createDefault(), JsonObjectMapper.DEFAULT_INSTANCE);

        synchronized (this) {
            this.persistence = buildPersistence(
                    runnerId,
                    coreConfiguration,
                    cloudConfiguration,
                    requestBuilderFactory
            );
        }
    }

    @Override
    public CloudAuditPersistenceImpl getPersistence() {
        return persistence;
    }

    @NotNull
    private CloudAuditPersistenceImpl buildPersistence(RunnerId runnerId,
                                                       CoreConfigurable coreConfiguration,
                                                       CloudConfigurable cloudConfiguration,
                                                       Http.RequestBuilderFactory requestBuilderFactory) {
        AuthManager authManager = new AuthManager(
                cloudConfiguration.getApiToken(),
                cloudConfiguration.getServiceName(),
                cloudConfiguration.getEnvironmentName(),
                getAuthClient(cloudConfiguration, requestBuilderFactory));
        AuthResponse authResponse = authManager.authenticate();

        EnvironmentId environmentId = EnvironmentId.fromString(authResponse.getEnvironmentId());
        ServiceId serviceId = ServiceId.fromString(authResponse.getServiceId());

        LifecycleAuditWriter auditWriter = new HtttpAuditWriter(
                cloudConfiguration.getHost(),
                environmentId,
                serviceId,
                runnerId,
                cloudConfiguration.getApiVersion(),
                requestBuilderFactory,
                authManager
        );

        ExecutionPlanner executionPlanner = getExecutionPlanner(
                runnerId,
                coreConfiguration,
                cloudConfiguration,
                requestBuilderFactory,
                authManager,
                environmentId,
                serviceId);

        return new CloudAuditPersistenceImpl(
                environmentId,
                serviceId,
                authResponse.getJwt(),
                auditWriter,
                executionPlanner,
                getCloser(requestBuilderFactory)
        );
    }

    @NotNull
    private HttpAuthClient getAuthClient(CloudConfigurable cloudConfiguration,
                                         Http.RequestBuilderFactory requestBuilderFactory) {
        return new HttpAuthClient(
                cloudConfiguration.getHost(),
                cloudConfiguration.getApiVersion(),
                requestBuilderFactory);
    }

    @NotNull
    private ExecutionPlanner getExecutionPlanner(RunnerId runnerId,
                                                 CoreConfigurable coreConfiguration,
                                                 CloudConfigurable cloudConfiguration,
                                                 Http.RequestBuilderFactory requestBuilderFactory,
                                                 AuthManager authManager,
                                                 EnvironmentId environmentId,
                                                 ServiceId serviceId) {
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

        return new CloudExecutionPlanner(
                runnerId,
                executionPlannerClient,
                coreConfiguration,
                new CloudLockService(lockClient),
                null,
                TimeService.getDefault()
        );
    }

    @NotNull
    private Runnable getCloser(Http.RequestBuilderFactory requestBuilderFactory) {
        return () -> {
            if (requestBuilderFactory != null) {
                try {
                    requestBuilderFactory.close();
                } catch (IOException ex) {
                    logger.warn("Error closing request builder factory", ex);
                }
            }
        };
    }
}
