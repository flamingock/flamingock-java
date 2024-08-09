package flamingock.internal.legacy.importer.mongodb;


import io.changock.migration.api.annotations.NonLockGuarded;
import io.flamingock.core.api.annotations.ChangeUnit;
import io.flamingock.core.api.annotations.Execution;
import io.flamingock.core.api.annotations.SystemChange;

@SystemChange
@ChangeUnit(id = "mongock-legacy-importer-mongodb", order = "1")
public class MongockLegacyImporterChangeUnit {

    @Execution
    public void execution(@NonLockGuarded MongoDBLegacyImportConfiguration configuration) {
        System.out.println("\n\n" +
                "EXECUTED LEGACY IMPORTER(\n," +
                configuration.getEnvironmentId().toString() +
                "\n" +
                configuration.getServiceId().toString() +
                "\n" +
                configuration.getChangeUnitsCollection());

    }
}
