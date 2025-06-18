package io.flamingock.importer.adapter.dynamodb;

import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.importer.ImporterAdapter;

import java.util.Collections;
import java.util.List;

public class DynamoDbImporterAdapter implements ImporterAdapter {
    @Override
    public List<AuditEntry> getAuditEntries() {
        return Collections.emptyList();
    }
}
