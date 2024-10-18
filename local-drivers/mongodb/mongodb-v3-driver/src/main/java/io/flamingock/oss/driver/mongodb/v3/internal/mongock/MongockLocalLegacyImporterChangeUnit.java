package io.flamingock.oss.driver.mongodb.v3.internal.mongock;


import io.flamingock.core.api.annotations.ChangeUnit;
import io.flamingock.core.api.annotations.Execution;
import io.flamingock.core.api.annotations.SystemChange;

@SystemChange
@ChangeUnit(id = "mongock-local-legacy-importer-mongodb-3", order = "1")
public class MongockLocalLegacyImporterChangeUnit {

    @Execution
    public void execution(InternalMongockImporterConfiguration configuration) {
        if(configuration.getSourceCollection() == null) {
            throw new RuntimeException("AuditWriter not injected");
        }
        if(configuration.getAuditWriter() == null) {
            throw new RuntimeException("AuditWriter not injected");
        }

        if(true) {
            throw new RuntimeException("Mongock importer needs to be implemented");
        }
    }
}
