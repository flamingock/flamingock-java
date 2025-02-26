package io.flamingock.core.engine.audit.importer;

import io.flamingock.core.api.LocalSystemModule;
import io.flamingock.core.engine.audit.importer.changeunit.MongockImporterChangeUnit;
import io.flamingock.core.runtime.dependency.Dependency;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class ImporterModule implements LocalSystemModule {
    public static final List<Class<?>> MONGOCK_CHANGE_UNITS = Collections.singletonList(
            MongockImporterChangeUnit.class
    );

    public static final List<Class<?>> FROM_FLAMINGOCK_CHANGE_UNITS = Collections.singletonList(
            MongockImporterChangeUnit.class
    );
    public static final String FROM_MONGOCK_NAME = "from-mongock-importer";
    public static final String FROM_FLAMINGOCK_LOCAL_NAME = "from-flamingock-local-importer";

    private final ImporterReader importReader;
    private final boolean fromMongock;
    private List<Dependency> dependencies;

    public ImporterModule(ImporterReader importerReader) {
        this.fromMongock = importerReader.isFromMongock();
        this.importReader = importerReader;
    }

    @Override
    public void initialise() {
        dependencies = Collections.singletonList(
                new Dependency(ImporterReader.class, importReader)
        );
    }

    @Override
    public String getName() {
        return fromMongock ? FROM_MONGOCK_NAME : FROM_FLAMINGOCK_LOCAL_NAME;
    }

    @Override
    public Collection<Class<?>> getTaskClasses() {
        return fromMongock ? MONGOCK_CHANGE_UNITS : FROM_FLAMINGOCK_CHANGE_UNITS;
    }

    @Override
    public int getOrder() {
        return 0;
    }

    @Override
    public List<Dependency> getDependencies() {
        return dependencies;
    }

    @Override
    public boolean isBeforeUserStages() {
        return true;
    }
}
