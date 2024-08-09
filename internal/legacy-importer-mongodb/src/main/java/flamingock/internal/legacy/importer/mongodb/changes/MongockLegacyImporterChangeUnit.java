package flamingock.internal.legacy.importer.mongodb.changes;


import io.flamingock.core.api.annotations.ChangeUnit;
import io.flamingock.core.api.annotations.Execution;
import io.flamingock.core.api.annotations.SystemChange;
import flamingock.internal.legacy.importer.mongodb.MongoDBLegacyImportConfiguration;

@SystemChange
@ChangeUnit(id = "mongock-legacy-importer-mongodb", order = "1")
public class MongockLegacyImporterChangeUnit {

    @Execution
    public void execution(MongoDBLegacyImportConfiguration configuration) {

    }
}
