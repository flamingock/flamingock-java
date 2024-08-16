package flamingock.internal.legacy.importer.mongodb;

import io.flamingock.commons.utils.id.EnvironmentId;
import io.flamingock.commons.utils.id.ServiceId;

public class MongoDBLegacyImportConfiguration {

    private final String changeUnitsCollection;
    private final EnvironmentId environmentId;
    private final String jwt;
    private final ServiceId serviceId;

    public MongoDBLegacyImportConfiguration(EnvironmentId environmentId,
                                            ServiceId serviceId,
                                            String jwt,
                                            String changeUnitsCollection) {
        this.environmentId = environmentId;
        this.serviceId = serviceId;
        this.jwt = jwt;
        this.changeUnitsCollection = changeUnitsCollection;
    }

    public String getChangeUnitsCollection() {
        return changeUnitsCollection;
    }

    public EnvironmentId getEnvironmentId() {
        return environmentId;
    }

    public ServiceId getServiceId() {
        return serviceId;
    }

    public String getJwt() {
        return jwt;
    }
}
