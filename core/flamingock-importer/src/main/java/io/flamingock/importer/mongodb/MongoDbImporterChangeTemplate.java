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

package io.flamingock.importer.mongodb;

import com.mongodb.client.MongoDatabase;
import io.flamingock.core.api.annotations.Execution;
import io.flamingock.core.api.annotations.NonLockGuarded;
import io.flamingock.core.api.annotations.RollbackExecution;
import io.flamingock.core.api.template.AbstractChangeTemplate;
import io.flamingock.core.audit.AuditWriter;
import io.flamingock.core.pipeline.PipelineDescriptor;
import io.flamingock.importer.ImporterExecutor;
import io.flamingock.importer.ImporterTemplateConfiguration;

public class MongoDbImporterChangeTemplate extends AbstractChangeTemplate<ImporterTemplateConfiguration> {

    public MongoDbImporterChangeTemplate() {
        super(ImporterTemplateConfiguration.class);
    }

    @Execution
    public void execution(MongoDatabase db,
                          @NonLockGuarded AuditWriter auditWriter,
                          @NonLockGuarded PipelineDescriptor pipelineDescriptor) {
        String collectionName = "";//configuration.getShared().getOrigin();
        MongoDbImporterAdapter adapter = new MongoDbImporterAdapter(db, collectionName);
        ImporterExecutor.runImport(adapter, auditWriter, pipelineDescriptor);
    }

    @RollbackExecution
    public void rollback() {
        //TODO
    }

}
