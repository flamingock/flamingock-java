package io.flamingock.cloud;

import io.flamingock.commons.utils.JsonObjectMapper;
import io.flamingock.commons.utils.id.RunnerId;
import io.flamingock.commons.utils.TimeService;
import io.flamingock.commons.utils.http.Http;
import io.flamingock.commons.utils.id.EnvironmentId;
import io.flamingock.commons.utils.id.ServiceId;
import io.flamingock.core.builder.cloud.CloudConfigurable;
import io.flamingock.core.builder.core.CoreConfigurable;
import io.flamingock.core.cloud.CloudDriver;
import io.flamingock.core.cloud.api.auth.AuthResponse;
import io.flamingock.cloud.audit.HtttpAuditWriter;
import io.flamingock.cloud.auth.AuthManager;
import io.flamingock.cloud.auth.HttpAuthClient;
import io.flamingock.cloud.lock.CloudLockService;
import io.flamingock.cloud.lock.client.HttpLockServiceClient;
import io.flamingock.cloud.lock.client.LockServiceClient;
import io.flamingock.cloud.planner.CloudExecutionPlanner;
import io.flamingock.cloud.planner.client.ExecutionPlannerClient;
import io.flamingock.cloud.planner.client.HttpExecutionPlannerClient;
import io.flamingock.core.cloud.transaction.CloudTransactioner;
import io.flamingock.core.engine.audit.AuditWriter;
import io.flamingock.core.engine.execution.ExecutionPlanner;
import io.flamingock.core.context.ContextResolver;
import org.apache.http.impl.client.HttpClients;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class CloudDriverImpl  implements CloudDriver {
    private static final Logger logger = LoggerFactory.getLogger(CloudDriverImpl.class);
    private CloudEngineImpl engine;


    @Override
    public void initialize(ContextResolver dependencyContext) {
        RunnerId runnerId = dependencyContext.getRequiredDependencyValue(RunnerId.class);

        CoreConfigurable coreConfiguration = dependencyContext.getRequiredDependencyValue(CoreConfigurable.class);
        CloudConfigurable cloudConfiguration = dependencyContext.getRequiredDependencyValue(CloudConfigurable.class);
        CloudTransactioner transactioner = dependencyContext.getDependencyValue(CloudTransactioner.class).orElse(null);

        Http.RequestBuilderFactory requestBuilderFactory =
                Http.builderFactory(HttpClients.createDefault(), JsonObjectMapper.DEFAULT_INSTANCE);

        synchronized (this) {
            this.engine = buildEngine(
                    runnerId,
                    coreConfiguration,
                    cloudConfiguration,
                    transactioner,
                    requestBuilderFactory
            );
        }
    }

    @Override
    public CloudEngineImpl getEngine() {
        return engine;
    }

    @NotNull
    private CloudEngineImpl buildEngine(RunnerId runnerId,
                                        CoreConfigurable coreConfiguration,
                                        CloudConfigurable cloudConfiguration,
                                        CloudTransactioner transactioner,
                                        Http.RequestBuilderFactory requestBuilderFactory) {
        AuthManager authManager = new AuthManager(
                cloudConfiguration.getApiToken(),
                cloudConfiguration.getServiceName(),
                cloudConfiguration.getEnvironmentName(),
                getAuthClient(cloudConfiguration, requestBuilderFactory));
        AuthResponse authResponse = authManager.authenticate();

        EnvironmentId environmentId = EnvironmentId.fromString(authResponse.getEnvironmentId());
        ServiceId serviceId = ServiceId.fromString(authResponse.getServiceId());

        AuditWriter auditWriter = new HtttpAuditWriter(
                cloudConfiguration.getHost(),
                environmentId,
                serviceId,
                runnerId,
                cloudConfiguration.getApiVersion(),
                requestBuilderFactory,
                authManager
        );

        if (transactioner != null) {
            transactioner.initialize();
        }

        ExecutionPlanner executionPlanner = getExecutionPlanner(
                runnerId,
                coreConfiguration,
                cloudConfiguration,
                requestBuilderFactory,
                transactioner,
                authManager,
                environmentId,
                serviceId);

        return new CloudEngineImpl(
                environmentId,
                serviceId,
                authResponse.getJwt(),
                auditWriter,
                executionPlanner,
                transactioner,
                getCloser(requestBuilderFactory, transactioner)
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
                                                 CloudTransactioner transactioner,
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
                transactioner,
                TimeService.getDefault()
        );
    }

    @NotNull
    private Runnable getCloser(Http.RequestBuilderFactory requestBuilderFactory,
                               CloudTransactioner transactioner) {
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
