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

import io.flamingock.internal.core.configuration.cloud.CloudConfiguration;
import io.flamingock.internal.core.configuration.core.CoreConfiguration;
import io.flamingock.internal.core.configuration.community.CommunityConfiguration;
import io.flamingock.internal.core.external.store.CloudAuditStore;
import io.flamingock.internal.core.context.SimpleContext;
import io.flamingock.internal.core.plugin.DefaultPluginManager;
import io.flamingock.internal.core.external.store.CommunityAuditStore;

import java.util.Optional;

public final class FlamingockFactory {

    private FlamingockFactory() {
    }

    public static AbstractChangeRunnerBuilder<?,?> getEditionAwareBuilder(CoreConfiguration coreConfiguration,
                                                                        CloudConfiguration cloudConfiguration,
                                                                        CommunityConfiguration communityConfiguration,
                                                                        CommunityAuditStore communityAuditStore) {
        Optional<CloudAuditStore> cloudAuditStore = CloudAuditStore.get();
        if (cloudAuditStore.isPresent()) {
            return new CloudChangeRunnerBuilder(
                    coreConfiguration,
                    cloudConfiguration,
                    new SimpleContext(),
                    new DefaultPluginManager(),
                    cloudAuditStore.get());
        } else {
            return new CommunityChangeRunnerBuilder(
                    coreConfiguration,
                    communityConfiguration,
                    new SimpleContext(),
                    new DefaultPluginManager())
                    .setAuditStore(communityAuditStore);
        }
    }

    public static CloudChangeRunnerBuilder getCloudBuilder() {
        return new CloudChangeRunnerBuilder(
                new CoreConfiguration(),
                new CloudConfiguration(),
                new SimpleContext(),
                new DefaultPluginManager(),
                CloudAuditStore.get().orElseThrow(() -> new RuntimeException("Cloud edition is required but was not imported. Please import flamingock-cloud artefact to your project.")));
    }

    public static CommunityChangeRunnerBuilder getCommunityBuilder() {
        //auditStore must be set later via builder
        return new CommunityChangeRunnerBuilder(
                new CoreConfiguration(),
                new CommunityConfiguration(),
                new SimpleContext(),
                new DefaultPluginManager()
        );
    }

}
