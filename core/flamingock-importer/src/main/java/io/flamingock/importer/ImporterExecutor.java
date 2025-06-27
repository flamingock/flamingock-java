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

package io.flamingock.importer;


import io.flamingock.importer.util.ImporterLogger;
import io.flamingock.importer.util.PipelineHelper;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.audit.AuditWriter;
import io.flamingock.internal.common.core.error.FlamingockException;
import io.flamingock.internal.common.core.pipeline.PipelineDescriptor;

import java.util.List;

/**
 * This changeUnit imports the Mongock data in the database to Flamingock(local or cloud).
 * Although we could have just one ChangeUnit for importing
 * - Mongock            to Flamingock local
 * - Mongock            to Flamingock CLoud
 * - Flamingock local   to Flamingock cloud
 * We need to differentiate it, as we can have two steps(Mongock to Flamingock local to Flamingock Cloud)
 */
public final class ImporterExecutor {
    private static final ImporterLogger importerLogger = new ImporterLogger("Flamingock Importer");

    private ImporterExecutor() {

    }

    /**
     * Reads from a database (either Mongock or Flamingock local) and writes to Flamingock (either local or cloud).
     * The supported migration paths are:
     * - Mongock            to Flamingock-ce
     * - Mongock            to Flamingock Cloud
     * - Flamingock-ce     to Flamingock Cloud
     *
     * @param importerAdapter    Database log reader.
     * @param auditWriter        Destination writer.
     * @param pipelineDescriptor Structure containing all information about the changes and tasks to execute.
     */
    public static void runImport(ImporterAdapter importerAdapter,
                                 ImportConfiguration importConfiguration,
                                 AuditWriter auditWriter,
                                 PipelineDescriptor pipelineDescriptor) {
        PipelineHelper pipelineHelper = new PipelineHelper(pipelineDescriptor);

        importerLogger.logStart(importerAdapter, auditWriter);

        List<AuditEntry> auditEntries = importerAdapter.getAuditEntries();
        if(importConfiguration.isFailOnEmptyOrigin() &&  auditEntries.isEmpty()) {
            throw new FlamingockException(
                    String.format("No audit entries found when importing from '%s'. " +
                                    "Set 'failOnEmptyOrigin=false' in the import changeUnit to disable this validation.",
                            importConfiguration.getOrigin())
            );
        }

        auditEntries.forEach(auditEntryFromOrigin -> {
            //This is the taskId present in the pipeline. If it's a system change or '..._before' won't appear
            AuditEntry auditEntryWithStageId = auditEntryFromOrigin.copyWithNewIdAndStageId(
                    pipelineHelper.getStorableTaskId(auditEntryFromOrigin),
                    pipelineHelper.getStageId(auditEntryFromOrigin));
            auditWriter.writeEntry(auditEntryWithStageId);
        });
    }



}
