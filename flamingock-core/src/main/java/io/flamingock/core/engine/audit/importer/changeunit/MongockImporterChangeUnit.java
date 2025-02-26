package io.flamingock.core.engine.audit.importer.changeunit;


import io.flamingock.core.api.annotations.ChangeUnit;
import io.flamingock.core.api.annotations.Execution;
import io.flamingock.core.api.annotations.NonLockGuarded;
import io.flamingock.core.api.annotations.SystemChange;
import io.flamingock.core.engine.audit.AuditWriter;
import io.flamingock.core.engine.audit.importer.ImporterReader;
import io.flamingock.core.pipeline.PipelineDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This changeUnit imports the Mongock data in the database to Flamingock(local or cloud).
 * Although we could have just one ChangeUnit for importing
 * - Mongock            -> Flamingock local
 * - Mongock            -> Flamingock CLoud
 * - Flamingock local   -> Flamingock cloud
 * We need to differentiate it, as we can have two steps(Mongock -> Flamingock local -> Flamingock Cloud)
 */
@SystemChange
@ChangeUnit(id = MongockImporterChangeUnit.IMPORTER_FROM_MONGOCK, order = "1")
public class MongockImporterChangeUnit {
    public static final String IMPORTER_FROM_MONGOCK = "importer-from-mongock";

    @Execution
    public void execution(@NonLockGuarded ImporterReader importerReader,
                          @NonLockGuarded AuditWriter auditWriter,
                          @NonLockGuarded PipelineDescriptor pipelineDescriptor) {
        ImporterExecutor.runImport(importerReader, auditWriter, pipelineDescriptor);

    }


}
