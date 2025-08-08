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
package io.flamingock.importer.couchbase;

import com.couchbase.client.core.io.CollectionIdentifier;
import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.Cluster;
import io.flamingock.api.annotations.Execution;
import io.flamingock.api.annotations.NonLockGuarded;
import io.flamingock.api.annotations.RollbackExecution;
import io.flamingock.importer.AbstractImporterChangeTemplate;
import io.flamingock.importer.ImporterExecutor;
import io.flamingock.internal.common.core.audit.AuditWriter;
import io.flamingock.internal.common.core.pipeline.PipelineDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CouchbaseImporterChangeTemplate extends AbstractImporterChangeTemplate {
    private static final Logger logger = LoggerFactory.getLogger("CouchbaseImporterChangeTemplate");

    @Execution
    public void execution(Cluster cluster,
                          Bucket bucket, //TODO this is the main bucket, we need to provide a way to get a custom bucket (target system)
                          @NonLockGuarded AuditWriter auditWriter,
                          @NonLockGuarded PipelineDescriptor pipelineDescriptor) {
        logger.info("Starting audit log migration from Mongock to Flamingock local audit store[Couchbase]");
        //TODO get the scope from configuration
        CouchbaseImporterAdapter adapter = new CouchbaseImporterAdapter(cluster, bucket.name(), CollectionIdentifier.DEFAULT_SCOPE, configuration.getOrigin());
        ImporterExecutor.runImport(adapter, configuration, auditWriter, pipelineDescriptor);
        logger.info("Finished audit log migration from Mongock to Flamingock local audit store[Couchbase]");
    }

    @RollbackExecution
    public void rollback() {
        // TODO: Implement rollback logic if needed
    }
}