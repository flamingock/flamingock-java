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
package io.flamingock.support.inmemory;

import io.flamingock.internal.common.core.context.Context;
import io.flamingock.internal.core.builder.CommunityChangeRunnerBuilder;
import io.flamingock.internal.core.configuration.community.CommunityConfiguration;
import io.flamingock.internal.core.configuration.core.CoreConfiguration;
import io.flamingock.internal.core.context.SimpleContext;
import io.flamingock.internal.core.plugin.DefaultPluginManager;
import io.flamingock.internal.core.plugin.PluginManager;

/**
 * Specialized Flamingock builder for testing purposes.
 * 
 * <p>This builder extends CommunityFlamingockBuilder and is pre-configured with test-specific
 * settings and AuditStores. It's created by TestKit implementations and provides the same API
 * as production builders but optimized for testing scenarios.</p>
 * 
 * <p><strong>Usage:</strong></p>
 * <pre>{@code
 * TestFlamingockBuilder builder = testKit.createBuilder();
 *
 * // Configure for testing (common settings)
 * builder.addTargetSystem(new DefaultTargetSystem("test-system"));
 *
 * // Build and run
 * builder.build().run();
 * }</pre>
 * 
 * <p>Typically used through TestKit.createBuilder() rather than directly instantiated.</p>
 */
public class InMemoryFlamingockBuilder extends CommunityChangeRunnerBuilder {


    public static InMemoryFlamingockBuilder create() {
        return new InMemoryFlamingockBuilder(
                new CoreConfiguration(),
                new CommunityConfiguration(),
                new SimpleContext(),
                new DefaultPluginManager(),
                InMemoryAuditStore.create()
        );
    }

    private InMemoryFlamingockBuilder(CoreConfiguration coreConfiguration,
                                     CommunityConfiguration communityConfiguration,
                                     Context dependencyInjectableContext,
                                     PluginManager pluginManager,
                                     InMemoryAuditStore auditStore) {
        super(coreConfiguration, communityConfiguration, dependencyInjectableContext, pluginManager, auditStore);
        this.auditStore = auditStore;
    }
    
    @Override
    protected InMemoryFlamingockBuilder getSelf() {
        return this;
    }

    public InMemoryAuditStore getAuditStore() {
        return  (InMemoryAuditStore) auditStore;
    }
}