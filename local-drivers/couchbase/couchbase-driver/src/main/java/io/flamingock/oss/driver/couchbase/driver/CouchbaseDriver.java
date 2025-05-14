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

package io.flamingock.oss.driver.couchbase.driver;

import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.Collection;
import io.flamingock.commons.utils.RunnerId;
import io.flamingock.core.api.exception.FlamingockException;
import io.flamingock.core.builder.core.CoreConfigurable;
import io.flamingock.core.builder.local.CommunityConfigurable;
import io.flamingock.core.community.LocalEngine;
import io.flamingock.core.community.driver.LocalDriver;
import io.flamingock.core.runtime.dependency.DependencyContext;
import io.flamingock.oss.driver.couchbase.CouchbaseConfiguration;
import io.flamingock.oss.driver.couchbase.internal.CouchbaseEngine;

public class CouchbaseDriver implements LocalDriver {

    private Collection collection;

    private Cluster cluster;

    private CouchbaseConfiguration driverConfiguration;

    public CouchbaseDriver() {
    }

    @Override
    public void initialize(DependencyContext dependencyContext) {
        this.cluster = dependencyContext
                .getDependencyValue(Cluster.class)
                .orElseThrow(() -> new FlamingockException("Couchbase Cluster is needed to be added as dependency"));
        this.collection = dependencyContext
                .getDependencyValue(Collection.class)
                .orElseThrow(() -> new FlamingockException("Couchbase Collection is needed to be added as dependency"));
        this.driverConfiguration = generateConfig(dependencyContext);
    }

    public CouchbaseConfiguration generateConfig(DependencyContext dependencyContext) {
        CouchbaseConfiguration configuration = dependencyContext
                .getDependencyValue(CouchbaseConfiguration.class)
                .orElse(CouchbaseConfiguration.getDefault());
        dependencyContext.getPropertyAs("couchbase.autoCreate", boolean.class)
                .ifPresent(configuration::setIndexCreation);
        dependencyContext.getPropertyAs("couchbase.auditRepositoryName", String.class)
                .ifPresent(d -> {
                    //TODO
                });
        dependencyContext.getPropertyAs("couchbase.lockRepositoryName", String.class)
                .ifPresent(d -> {
                    //TODO
                });
        return configuration;
    }

    @Override
    public LocalEngine initializeAndGetEngine(RunnerId runnerId,
                                              CoreConfigurable coreConfiguration,
                                              CommunityConfigurable localConfiguration) {
        CouchbaseEngine couchbaseEngine = new CouchbaseEngine(
                cluster,
                collection,
                coreConfiguration,
                localConfiguration,
                driverConfiguration);
        couchbaseEngine.initialize(runnerId);
        return couchbaseEngine;
    }

}
