package io.flamingock.importer.couchbase;

import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.importer.ImporterAdapter;

import java.util.Collections;
import java.util.List;

public class CouchbaseImporterAdapter implements ImporterAdapter {
    @Override
    public List<AuditEntry> getAuditEntries() {
        return Collections.emptyList();
    }
}
