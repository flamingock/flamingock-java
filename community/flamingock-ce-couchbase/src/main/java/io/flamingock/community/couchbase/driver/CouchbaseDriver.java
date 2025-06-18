/*
 * Copyright 2023 Flamingock (https://oss.flamingock.io)
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

package io.flamingock.community.couchbase.driver;

import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.Collection;
import io.flamingock.internal.util.id.RunnerId;
import io.flamingock.internal.core.builder.core.CoreConfigurable;
import io.flamingock.internal.core.builder.local.CommunityConfigurable;
import io.flamingock.internal.core.community.LocalEngine;
import io.flamingock.internal.core.community.driver.LocalDriver;
import io.flamingock.internal.common.core.context.ContextResolver;
import io.flamingock.community.couchbase.CouchbaseConfiguration;
import io.flamingock.community.couchbase.internal.CouchbaseEngine;

public class CouchbaseDriver implements LocalDriver {

    private Cluster cluster;
    private Collection collection;
    private RunnerId runnerId;
    private CoreConfigurable coreConfiguration;
    private CommunityConfigurable communityConfiguration;
    private CouchbaseConfiguration driverConfiguration;

    public CouchbaseDriver() {
    }

    @Override
    public void initialize(ContextResolver dependencyContext) {
        runnerId = dependencyContext.getRequiredDependencyValue(RunnerId.class);

        coreConfiguration = dependencyContext.getRequiredDependencyValue(CoreConfigurable.class);
        communityConfiguration = dependencyContext.getRequiredDependencyValue(CommunityConfigurable.class);

        this.cluster = dependencyContext.getRequiredDependencyValue(Cluster.class);
        this.collection = dependencyContext.getRequiredDependencyValue(Collection.class);

        this.driverConfiguration = dependencyContext.getDependencyValue(CouchbaseConfiguration.class)
                .orElse(new CouchbaseConfiguration());
        this.driverConfiguration.mergeConfig(dependencyContext);
    }

    @Override
    public LocalEngine getEngine() {
        CouchbaseEngine couchbaseEngine = new CouchbaseEngine(
                cluster,
                collection,
                coreConfiguration,
                communityConfiguration,
                driverConfiguration);
        couchbaseEngine.initialize(runnerId);
        return couchbaseEngine;
    }

}
