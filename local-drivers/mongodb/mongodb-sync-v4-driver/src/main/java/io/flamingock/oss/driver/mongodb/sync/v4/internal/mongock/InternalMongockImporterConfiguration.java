package io.flamingock.oss.driver.mongodb.sync.v4.internal.mongock;

import com.mongodb.client.MongoCollection;
import io.flamingock.core.api.annotations.NonLockGuarded;
import io.flamingock.core.engine.audit.AuditWriter;
import org.bson.Document;

@NonLockGuarded
public class InternalMongockImporterConfiguration {

    private final MongoCollection<Document> sourceCollection;

    private final AuditWriter auditWriter;

    public InternalMongockImporterConfiguration(MongoCollection<Document> sourceCollection, AuditWriter auditWriter) {
        this.sourceCollection = sourceCollection;
        this.auditWriter = auditWriter;
    }

    public MongoCollection<Document> getSourceCollection() {
        return sourceCollection;
    }

    public AuditWriter getAuditWriter() {
        return auditWriter;
    }

    @Override
    public String toString() {
        return "MongockLegacyImporterConfiguration{" + "mongoDatabase=" + sourceCollection.toString() +
                ", auditWriter=" + auditWriter.toString() +
                '}';
    }
}
