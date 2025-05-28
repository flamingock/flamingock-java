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

package io.flamingock.internal.core.engine.audit.importer.changeunit;


import io.flamingock.core.api.annotations.Change;
import io.flamingock.core.api.annotations.Execution;
import io.flamingock.core.api.annotations.NonLockGuarded;
import io.flamingock.internal.core.engine.audit.AuditWriter;
import io.flamingock.internal.core.engine.audit.importer.ImporterReader;
import io.flamingock.internal.core.pipeline.PipelineDescriptor;

/**
 * This changeUnit imports the Mongock data in the database to Flamingock(local or cloud).
 * Although we could have just one ChangeUnit for importing
 * - Mongock            to Flamingock local
 * - Mongock            to Flamingock CLoud
 * - Flamingock local   to Flamingock cloud
 * We need to differentiate it, as we can have two steps(Mongock to Flamingock local to Flamingock Cloud)
 */
@Change(id = MongockImporterChangeUnit.IMPORTER_FROM_MONGOCK, order = "001")
public class MongockImporterChangeUnit {
    public static final String IMPORTER_FROM_MONGOCK = "importer-from-mongock";

    @Execution
    public void execution(@NonLockGuarded ImporterReader importerReader,
                          @NonLockGuarded AuditWriter auditWriter,
                          @NonLockGuarded PipelineDescriptor pipelineDescriptor) {
        ImporterExecutor.runImport(importerReader, auditWriter, pipelineDescriptor);

    }


}
