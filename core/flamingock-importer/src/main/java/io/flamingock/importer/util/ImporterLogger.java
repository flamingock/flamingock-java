package io.flamingock.importer.util;

import io.flamingock.importer.ImporterAdapter;
import io.flamingock.internal.common.core.audit.AuditWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ImporterLogger {

    private final Logger logger;

    public ImporterLogger(String loggerName) {
        this.logger = LoggerFactory.getLogger(loggerName);;
    }

    public void logStart(ImporterAdapter importerAdapter, AuditWriter auditWriter) {
        logger.info("Importing audit log from [ {}] to Flamingock[{}]",
                importerAdapter.getClass().getSimpleName(),
                auditWriter.isCloud() ? "Cloud" : "CE");
    }
}
