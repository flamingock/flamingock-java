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
package io.flamingock.community.couchbase.driver;

import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.Cluster;
import io.flamingock.internal.common.core.error.FlamingockException;
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
    private Bucket bucket;
    private RunnerId runnerId;
    private CoreConfigurable coreConfiguration;
    private CommunityConfigurable communityConfiguration;
    private CouchbaseConfiguration driverConfiguration;

    public CouchbaseDriver() {
    }

    @Override
    public void initialize(ContextResolver baseContext) {
        runnerId = baseContext.getRequiredDependencyValue(RunnerId.class);

        coreConfiguration = baseContext.getRequiredDependencyValue(CoreConfigurable.class);
        communityConfiguration = baseContext.getRequiredDependencyValue(CommunityConfigurable.class);

        this.cluster = baseContext.getRequiredDependencyValue(Cluster.class);
        this.bucket = baseContext.getRequiredDependencyValue(Bucket.class);

        this.driverConfiguration = baseContext.getDependencyValue(CouchbaseConfiguration.class).orElse(new CouchbaseConfiguration());
        this.driverConfiguration.mergeConfig(baseContext);

        this.validate();
    }

    @Override
    public LocalEngine getEngine() {
        CouchbaseEngine couchbaseEngine = new CouchbaseEngine(
                cluster,
                bucket,
                coreConfiguration,
                communityConfiguration,
                driverConfiguration);
        couchbaseEngine.initialize(runnerId);
        return couchbaseEngine;
    }

    private void validate() {
        if (driverConfiguration.getAuditRepositoryName() == null || driverConfiguration.getAuditRepositoryName().trim().isEmpty()) {
            throw new FlamingockException("The 'auditRepositoryName' property is required.");
        }

        if (driverConfiguration.getLockRepositoryName() == null || driverConfiguration.getLockRepositoryName().trim().isEmpty()) {
            throw new FlamingockException("The 'lockRepositoryName' property is required.");
        }

        if (driverConfiguration.getAuditRepositoryName().trim().equalsIgnoreCase(driverConfiguration.getLockRepositoryName().trim())) {
            throw new FlamingockException("The 'auditRepositoryName' and 'lockRepositoryName' properties must not be the same.");
        }
    }
}
