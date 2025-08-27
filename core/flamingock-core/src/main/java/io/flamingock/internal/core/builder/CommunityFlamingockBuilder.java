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

import io.flamingock.internal.core.plan.community.CommunityExecutionPlanner;
import io.flamingock.internal.core.store.CommunityAuditStore;
import io.flamingock.internal.core.configuration.core.CoreConfiguration;
import io.flamingock.internal.core.configuration.community.CommunityConfiguration;
import io.flamingock.internal.core.configuration.community.CommunityConfigurator;
import io.flamingock.internal.common.core.context.Context;
import io.flamingock.internal.core.store.AuditStore;
import io.flamingock.internal.core.plan.ExecutionPlanner;
import io.flamingock.internal.core.plugin.PluginManager;
import io.flamingock.internal.util.id.RunnerId;

public class CommunityFlamingockBuilder
        extends AbstractFlamingockBuilder<CommunityFlamingockBuilder>
        implements CommunityConfigurator<CommunityFlamingockBuilder> {

    private final CommunityConfiguration communityConfiguration;


    protected CommunityFlamingockBuilder(CoreConfiguration coreConfiguration,
                                         CommunityConfiguration communityConfiguration,
                                         Context dependencyInjectableContext,
                                         PluginManager pluginManager) {
        super(coreConfiguration, dependencyInjectableContext, pluginManager);
        this.communityConfiguration = communityConfiguration;
    }

    @Override
    protected CommunityFlamingockBuilder getSelf() {
        return this;
    }

    @Override
    protected void doUpdateContext() {
        addDependency(FlamingockEdition.COMMUNITY);
        addDependency(communityConfiguration);
    }

    @Override
    protected ExecutionPlanner buildExecutionPlanner(RunnerId runnerId) {

        return CommunityExecutionPlanner.builder()
                .setRunnerId(runnerId)
                .setAuditReader(auditStore.getPersistence())
                .setLockService(((CommunityAuditStore) auditStore).getLockService())
                .setCoreConfigurable(coreConfiguration)
                .build();
    }

    public CommunityFlamingockBuilder setAuditStore(AuditStore<?> auditStore) {
        this.auditStore = auditStore;
        return this;
    }

}
