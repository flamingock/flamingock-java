package io.flamingock.core.engine.audit.importer.changeunit;


import io.flamingock.core.api.annotations.Change;
import io.flamingock.core.api.annotations.Execution;
import io.flamingock.core.api.annotations.NonLockGuarded;
import io.flamingock.core.engine.audit.AuditWriter;
import io.flamingock.core.engine.audit.importer.ImporterReader;
import io.flamingock.core.pipeline.PipelineDescriptor;

/**
 * This changeUnit imports the Mongock data in the database to Flamingock(local or cloud).
 * Although we could have just one ChangeUnit for importing
 * - Mongock            to Flamingock local
 * - Mongock            to Flamingock CLoud
 * - Flamingock local   to Flamingock cloud
 * We need to differentiate it, as we can have two steps(Mongock to Flamingock local to Flamingock Cloud)
 */
@Change(id = MongockImporterChangeUnit.IMPORTER_FROM_MONGOCK, order = "1")
public class MongockImporterChangeUnit {
    public static final String IMPORTER_FROM_MONGOCK = "importer-from-mongock";

    @Execution
    public void execution(@NonLockGuarded ImporterReader importerReader,
                          @NonLockGuarded AuditWriter auditWriter,
                          @NonLockGuarded PipelineDescriptor pipelineDescriptor) {
        ImporterExecutor.runImport(importerReader, auditWriter, pipelineDescriptor);

    }


}
