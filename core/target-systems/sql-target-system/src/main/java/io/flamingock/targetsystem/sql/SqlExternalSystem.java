package io.flamingock.targetsystem.sql;

import io.flamingock.api.external.ExternalSystem;

import javax.sql.DataSource;

public interface SqlExternalSystem extends ExternalSystem {
    DataSource getDataSource();
}
