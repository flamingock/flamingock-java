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

import com.couchbase.client.java.Cluster;
import io.flamingock.api.annotations.Execution;
import io.flamingock.api.annotations.NonLockGuarded;
import io.flamingock.api.annotations.RollbackExecution;
import io.flamingock.importer.AbstractImporterChangeTemplate;
import io.flamingock.importer.ImporterExecutor;
import io.flamingock.internal.common.core.audit.AuditWriter;
import io.flamingock.internal.common.core.pipeline.PipelineDescriptor;

public class CouchbaseImporterChangeTemplate extends AbstractImporterChangeTemplate {

    @Execution
    public void execution(Cluster cluster,
                          @NonLockGuarded AuditWriter auditWriter,
                          @NonLockGuarded PipelineDescriptor pipelineDescriptor) {
        CouchbaseImporterAdapter adapter = new CouchbaseImporterAdapter(cluster, configuration.getOrigin());
        ImporterExecutor.runImport(adapter, configuration, auditWriter, pipelineDescriptor);
    }

    @RollbackExecution
    public void rollback() {
        // TODO: Implement rollback logic if needed
    }
}