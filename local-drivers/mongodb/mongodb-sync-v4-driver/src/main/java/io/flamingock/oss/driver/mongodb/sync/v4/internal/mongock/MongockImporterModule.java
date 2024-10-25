package io.flamingock.oss.driver.mongodb.sync.v4.internal.mongock;

import com.mongodb.client.MongoCollection;
import io.flamingock.core.api.LocalSystemModule;
import io.flamingock.core.engine.audit.AuditWriter;
import io.flamingock.core.runtime.dependency.Dependency;
import org.bson.Document;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class MongockImporterModule implements LocalSystemModule {
    public static final List<Class<?>> TASK_CLASSES = Collections.singletonList(
            MongockLocalLegacyImporterChangeUnit.class
    );
    private List<Dependency> dependencies;
    private final MongoCollection<Document> sourceCollection;
    private final AuditWriter auditWriter;

    public MongockImporterModule(MongoCollection<Document> sourceCollection, AuditWriter auditWriter) {
        this.sourceCollection = sourceCollection;
        this.auditWriter = auditWriter;
    }

    @Override
    public void initialise() {
        InternalMongockImporterConfiguration configuration = new InternalMongockImporterConfiguration(
                sourceCollection, auditWriter
        );
        dependencies = Collections.singletonList(
                new Dependency(InternalMongockImporterConfiguration.class, configuration)
        );
    }

    @Override
    public String getName() {
        return "mongodb-local-legacy-importer";
    }

    @Override
    public Collection<Class<?>> getTaskClasses() {
        return TASK_CLASSES;
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
