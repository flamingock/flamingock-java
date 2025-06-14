package io.flamingock.importer.mongodb.sync.v4;

import com.mongodb.client.MongoCollection;
import io.flamingock.internal.core.importer.ImporterReader;
import io.flamingock.internal.core.importer.model.MongockChangeEntry;
import io.flamingock.internal.core.importer.model.ChangeState;
import io.flamingock.internal.core.importer.model.ChangeType;
import io.flamingock.core.audit.AuditEntry;
import org.bson.Document;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

//TODO implement reading from Flamingock
public class MongoImporterReader implements ImporterReader {

    private final MongoCollection<Document> sourceCollection;

    public MongoImporterReader(MongoCollection<Document> sourceCollection) {
        this.sourceCollection = sourceCollection;
    }

    @Override
    public List<AuditEntry> getAuditEntries() {
        return sourceCollection.find()
                .into(new ArrayList<>())
                .stream()
                .map(MongoImporterReader::toAuditEntry)
                .collect(Collectors.toList());
    }

    @Override
    public String getSourceDescription() {
        return sourceCollection.getNamespace().toString();
    }

    @Override
    public boolean isFromMongock() {
        //TODO this will know if it's from mongock or flamingock, based on the data struture
        return true;
    }

    private static AuditEntry toAuditEntry(Document document) {
        MongockChangeEntry changeEntry = toChangeEntry(document);
        LocalDateTime timestamp = Instant.ofEpochMilli(changeEntry.getTimestamp().getTime())
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();

        if (changeEntry.getState() == ChangeState.IGNORED) {
            return null;
        }
        return new AuditEntry(
                changeEntry.getExecutionId(),
                null,
                changeEntry.getChangeId(),
                changeEntry.getAuthor(),
                timestamp,
                changeEntry.getState().toAuditStatus(),
                changeEntry.getType().toAuditType(),
                changeEntry.getChangeLogClass(),
                changeEntry.getChangeSetMethod(),
                changeEntry.getExecutionMillis(),
                changeEntry.getExecutionHostname(),
                changeEntry.getMetadata(),
                changeEntry.getSystemChange(),
                changeEntry.getErrorTrace()
        );
    }

    private static MongockChangeEntry toChangeEntry(Document document) {
        Date timestamp = document.getDate("timestamp");
        return new MongockChangeEntry(
                document.getString("executionId"),
                document.getString("changeId"),
                document.getString("author"),
                timestamp,
                ChangeState.valueOf(document.getString("state")),
                ChangeType.valueOf(document.getString("type")),
                document.getString("changeLogClass"),
                document.getString("changeSetMethod"),
                document.get("metadata"),
                document.getLong("executionMillis"),
                document.getString("executionHostName"),
                document.getString("errorTrace"),
                document.getBoolean("systemChange"),
                timestamp
        );
    }
}
