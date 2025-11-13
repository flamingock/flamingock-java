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
package io.flamingock.importer.mongodb;

import com.mongodb.client.MongoDatabase;
import io.flamingock.api.annotations.Apply;
import io.flamingock.api.annotations.NonLockGuarded;
import io.flamingock.api.annotations.Rollback;
import io.flamingock.importer.AbstractImporterChangeTemplate;
import io.flamingock.importer.ImportConfiguration;
import io.flamingock.importer.ImporterExecutor;
import io.flamingock.internal.common.core.audit.AuditWriter;
import io.flamingock.internal.common.core.pipeline.PipelineDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MongoDBImporterChangeTemplate extends AbstractImporterChangeTemplate<ImportConfiguration> {

    private static final Logger logger = LoggerFactory.getLogger("MongoDBImporterChangeTemplate");

    public MongoDBImporterChangeTemplate() {
        super(ImportConfiguration.class);
    }

    @Apply
    public void apply(MongoDatabase db,
                          @NonLockGuarded AuditWriter auditWriter,
                          @NonLockGuarded PipelineDescriptor pipelineDescriptor) {
        logger.info("Starting audit log migration from Mongock to Flamingock local audit store[MongoDB]");
        MongoDBImporterAdapter adapter = new MongoDBImporterAdapter(db, configuration.getOrigin());
        ImporterExecutor.runImport(adapter, configuration, auditWriter, pipelineDescriptor);
        logger.info("Finished audit log migration from Mongock to Flamingock local audit store[MongoDB]");
    }

    @Rollback
    public void rollback() {
        //TODO
    }

}
