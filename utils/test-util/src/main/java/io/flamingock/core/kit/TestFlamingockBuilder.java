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
package io.flamingock.core.kit;

import io.flamingock.internal.core.builder.CommunityFlamingockBuilder;
import io.flamingock.internal.core.builder.core.CoreConfiguration;
import io.flamingock.internal.core.builder.local.CommunityConfiguration;
import io.flamingock.internal.common.core.context.Context;
import io.flamingock.internal.core.community.store.LocalAuditStore;
import io.flamingock.internal.core.plugin.PluginManager;

/**
 * Specialized Flamingock builder for testing purposes.
 * 
 * <p>This builder extends CommunityFlamingockBuilder and is pre-configured with test-specific
 * settings and drivers. It's created by TestKit implementations and provides the same API
 * as production builders but optimized for testing scenarios.</p>
 * 
 * <p><strong>Usage:</strong></p>
 * <pre>{@code
 * TestFlamingockBuilder builder = testKit.createBuilder();
 * 
 * // Configure for testing (common settings)
 * builder.setRelaxTargetSystemValidation(true);
 * 
 * // Build and run
 * builder.build().run();
 * }</pre>
 * 
 * <p>Typically used through TestKit.createBuilder() rather than directly instantiated.</p>
 */
public class TestFlamingockBuilder extends CommunityFlamingockBuilder {

    public TestFlamingockBuilder(CoreConfiguration coreConfiguration,
                                CommunityConfiguration communityConfiguration,
                                Context dependencyInjectableContext,
                                PluginManager pluginManager,
                                LocalAuditStore auditStore) {
        super(coreConfiguration, communityConfiguration, dependencyInjectableContext, pluginManager);
        this.auditStore = auditStore;
    }
    
    @Override
    protected TestFlamingockBuilder getSelf() {
        return this;
    }

    public LocalAuditStore getAuditStore() {
        return (LocalAuditStore) auditStore;
    }
}