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


import io.flamingock.internal.common.core.audit.AuditWriter;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.pipeline.PipelineDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This changeUnit imports the Mongock data in the database to Flamingock(local or cloud).
 * Although we could have just one ChangeUnit for importing
 * - Mongock            to Flamingock local
 * - Mongock            to Flamingock CLoud
 * - Flamingock local   to Flamingock cloud
 * We need to differentiate it, as we can have two steps(Mongock to Flamingock local to Flamingock Cloud)
 */
public final class ImporterExecutor {
    private static final String errorTemplate = "importing changeUnit with id[%s] from database. It must be imported  to a flamingock stage";
    private static final Logger logger = LoggerFactory.getLogger(ImporterExecutor.class);

    private ImporterExecutor() {
    }

    /**
     * Reads from a database (either Mongock or Flamingock local) and writes to Flamingock (either local or cloud).
     * The supported migration paths are:
     * - Mongock            to Flamingock-ce
     * - Mongock            to Flamingock Cloud
     * - Flamingock-ce     to Flamingock Cloud
     * @param importerAdapter     Database log reader.
     * @param auditWriter        Destination writer.
     * @param pipelineDescriptor Structure containing all information about the changes and tasks to execute.
     */
    public static void runImport(ImporterAdapter importerAdapter,
                                 AuditWriter auditWriter,
                                 PipelineDescriptor pipelineDescriptor) {

        logger.info("Importing audit log from [ {}] to Flamingock[{}]",
                importerAdapter.getClass().getSimpleName(),
                auditWriter.isCloud() ? "Cloud" : "CE");

        importerAdapter.getAuditEntries().forEach(auditEntryFromOrigin -> {
            //This is the taskId present in the pipeline. If it's a system change or '..._before' won't appear
            AuditEntry auditEntryWithStageId = auditEntryFromOrigin.copyWithNewIdAndStageId(
                    getStorableTaskId(auditEntryFromOrigin),
                    getStageId(pipelineDescriptor, auditEntryFromOrigin));
            auditWriter.writeEntry(auditEntryWithStageId);
        });
    }

    private static String getStageId(PipelineDescriptor pipelineDescriptor, AuditEntry auditEntryFromOrigin) {
        if (Boolean.TRUE.equals(auditEntryFromOrigin.getSystemChange())) {
            return "mongock-legacy-system-changes";
        } else {
            String taskIdInPipeline = getBaseTaskId(auditEntryFromOrigin);
            return pipelineDescriptor.getStageByTask(taskIdInPipeline).orElseThrow(() -> new IllegalArgumentException(String.format(errorTemplate, getBaseTaskId(auditEntryFromOrigin))));
        }
    }

    private static String getBaseTaskId(AuditEntry auditEntry) {
        String originalTaskId = auditEntry.getTaskId();
        int index = originalTaskId.indexOf("_before");
        return index >= 0 ? originalTaskId.substring(0, index) : originalTaskId;
    }

    private static String getStorableTaskId(AuditEntry auditEntry) {
        return auditEntry.getTaskId();
    }

}
