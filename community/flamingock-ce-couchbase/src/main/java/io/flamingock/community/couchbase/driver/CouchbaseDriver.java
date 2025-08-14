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
import io.flamingock.api.targets.TargetSystem;
import io.flamingock.internal.common.core.error.FlamingockException;
import io.flamingock.internal.util.id.RunnerId;
import io.flamingock.internal.core.builder.core.CoreConfigurable;
import io.flamingock.internal.core.builder.local.CommunityConfigurable;
import io.flamingock.internal.core.community.LocalEngine;
import io.flamingock.internal.core.community.driver.LocalDriver;
import io.flamingock.internal.common.core.context.ContextResolver;
import io.flamingock.community.couchbase.CouchbaseConfiguration;
import io.flamingock.community.couchbase.internal.CouchbaseEngine;
import io.flamingock.targetsystem.couchbase.CouchbaseTargetSystem;

public class CouchbaseDriver implements LocalDriver {

    private RunnerId runnerId;
    private CouchbaseTargetSystem targetSystem;
    private CoreConfigurable coreConfiguration;
    private CommunityConfigurable communityConfiguration;
    private CouchbaseConfiguration driverConfiguration;

    public static CouchbaseDriver fromTargetSystem(CouchbaseTargetSystem targetSystem) {
        return new CouchbaseDriver(targetSystem);
    }

    public CouchbaseDriver() {
        this(null);
    }

    public CouchbaseDriver(CouchbaseTargetSystem targetSystem) {
        this.targetSystem = targetSystem;
    }

    @Override
    public void initialize(ContextResolver baseContext) {
        runnerId = baseContext.getRequiredDependencyValue(RunnerId.class);
        coreConfiguration = baseContext.getRequiredDependencyValue(CoreConfigurable.class);
        communityConfiguration = baseContext.getRequiredDependencyValue(CommunityConfigurable.class);
        this.driverConfiguration = baseContext.getDependencyValue(CouchbaseConfiguration.class).orElse(new CouchbaseConfiguration());
        driverConfiguration.mergeConfig(baseContext);
        this.validate();

        if (targetSystem == null) {
            Cluster cluster = baseContext.getRequiredDependencyValue(Cluster.class);
            Bucket bucket = baseContext.getRequiredDependencyValue(Bucket.class);
            targetSystem = new CouchbaseTargetSystem(DEFAULT_AUDIT_STORE_TARGET_SYSTEM)
                    .withCluster(cluster)
                    .withBucket(bucket)
                    .withScopeName(driverConfiguration.getScopeName())
                    .withAutoCreate(driverConfiguration.isAutoCreate());
        }
    }

    @Override
    public LocalEngine getEngine() {
        CouchbaseEngine couchbaseEngine = new CouchbaseEngine(
                targetSystem,
                coreConfiguration,
                communityConfiguration,
                driverConfiguration);
        couchbaseEngine.initialize(runnerId);
        return couchbaseEngine;
    }

    @Override
    public TargetSystem getTargetSystem() {
        return targetSystem;
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
