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
package io.flamingock.internal.core.builder;

import io.flamingock.internal.common.core.context.Context;
import io.flamingock.internal.core.configuration.community.CommunityConfiguration;
import io.flamingock.internal.core.configuration.community.CommunityConfigurator;
import io.flamingock.internal.core.configuration.core.CoreConfiguration;
import io.flamingock.internal.core.plan.ExecutionPlanner;
import io.flamingock.internal.core.plan.community.CommunityExecutionPlanner;
import io.flamingock.internal.core.plugin.PluginManager;
import io.flamingock.internal.core.external.store.CommunityAuditStore;
import io.flamingock.internal.common.core.context.Dependency;
import io.flamingock.internal.core.external.store.AuditCompactor;
import io.flamingock.internal.core.context.PriorityContext;
import io.flamingock.internal.util.id.RunnerId;

public class CommunityChangeRunnerBuilder
        extends AbstractChangeRunnerBuilder<CommunityAuditStore, CommunityChangeRunnerBuilder>
        implements CommunityConfigurator<CommunityChangeRunnerBuilder>,
        AuditStoreReceiver<CommunityChangeRunnerBuilder> {

    private final CommunityConfiguration communityConfiguration;


    public CommunityChangeRunnerBuilder(CoreConfiguration coreConfiguration,
                                        CommunityConfiguration communityConfiguration,
                                        Context dependencyInjectableContext,
                                        PluginManager pluginManager) {
        super(coreConfiguration, dependencyInjectableContext, pluginManager);
        this.communityConfiguration = communityConfiguration;
    }

    public CommunityChangeRunnerBuilder(CoreConfiguration coreConfiguration,
                                        CommunityConfiguration communityConfiguration,
                                        Context dependencyInjectableContext,
                                        PluginManager pluginManager,
                                        CommunityAuditStore auditStore) {
        super(coreConfiguration, dependencyInjectableContext, pluginManager, auditStore);
        this.communityConfiguration = communityConfiguration;
    }

    @Override
    protected CommunityChangeRunnerBuilder getSelf() {
        return this;
    }

    /**
     * Registers the audit store's compaction capability, which only Community has — the Cloud edition keeps
     * no local audit store to compact, so {@code getAuditCompactor()} is declared on
     * {@code CommunityAuditStore} rather than on {@code AuditStore}.
     * <p>
     * Registered as a deferring lambda rather than by calling {@code getAuditCompactor()} here, because
     * resolving it eagerly would be wrong in two different ways: the SQL store throws until its dialect
     * helper has been initialized, and the DynamoDB store creates its audit table as a side effect. Since
     * {@code AuditCompactor} has a single method, a lambda pushes both to the moment compaction is actually
     * invoked — by which point the store is fully initialized.
     * <p>
     * Nothing consumes this yet: {@code AuditCleanupChange} is written but not contributed to any pipeline.
     * The registration is inert until it is, and harmless in the meantime.
     */
    @Override
    protected void contributeEditionDependencies(PriorityContext hierarchicalContext) {
        hierarchicalContext.addDependency(new Dependency(AuditCompactor.class,
                (AuditCompactor) () -> auditStore.getAuditCompactor().compact()));
    }

    @Override
    protected void updateContextSpecific() {
        addDependency(FlamingockEdition.COMMUNITY);
        addDependency(communityConfiguration);
    }

    @Override
    protected ExecutionPlanner buildExecutionPlanner(RunnerId runnerId) {

        return CommunityExecutionPlanner.builder()
                .setRunnerId(runnerId)
                .setAuditReader(auditStore.getAuditReader())
                .setLockService(auditStore.getLockService())
                .setCoreConfigurable(coreConfiguration)
                .build();
    }

    @Override
    public CommunityChangeRunnerBuilder setAuditStore(CommunityAuditStore auditStore) {
        this.auditStore = auditStore;
        return this;
    }

}
