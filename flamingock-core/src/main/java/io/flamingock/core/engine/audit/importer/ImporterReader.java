package io.flamingock.core.engine.audit.importer;

import io.flamingock.core.engine.audit.writer.AuditEntry;

import java.util.List;

public interface ImporterReader {

    List<AuditEntry> getAuditEntries();

    String getSourceDescription();

    boolean isFromMongock();

}
