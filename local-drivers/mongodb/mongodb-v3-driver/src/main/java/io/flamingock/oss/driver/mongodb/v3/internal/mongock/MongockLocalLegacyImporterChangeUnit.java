package io.flamingock.oss.driver.mongodb.v3.internal.mongock;


import com.mongodb.client.MongoCollection;
import io.flamingock.core.api.annotations.ChangeUnit;
import io.flamingock.core.api.annotations.Execution;
import io.flamingock.core.api.annotations.SystemChange;
import io.flamingock.core.engine.audit.AuditWriter;
import io.flamingock.core.engine.audit.writer.AuditEntry;
import org.bson.Document;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SystemChange
@ChangeUnit(id = "mongock-local-legacy-importer-mongodb-3", order = "1")
public class MongockLocalLegacyImporterChangeUnit {


    @Execution
    public void execution(InternalMongockImporterConfiguration configuration) {
        MongoCollection<Document> sourceCollection = configuration.getSourceCollection();
        if (sourceCollection == null) {
            throw new RuntimeException("AuditWriter not injected");
        }
        AuditWriter auditWriter = configuration.getAuditWriter();
        if (auditWriter == null) {
            throw new RuntimeException("AuditWriter not injected");
        }

        List<AuditEntry> collect = sourceCollection.find()
                .into(new ArrayList<>())
                .stream()
                .map(MongockLocalLegacyImporterChangeUnit::toChangeEntry)
                .map(MongockLocalLegacyImporterChangeUnit::toAuditEntry)
                .collect(Collectors.toList());
        collect.forEach(auditWriter::writeEntry);

    }


    private static ChangeEntry toChangeEntry(Document document) {
        Date timestamp = document.getDate("timestamp");
        return new ChangeEntry(
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

    private static AuditEntry toAuditEntry(ChangeEntry changeEntry) {
        LocalDateTime timestamp = Instant.ofEpochMilli(changeEntry.timestamp.getTime())
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();

        if(changeEntry.state == ChangeState.IGNORED) {
            return null;
        }
        return new AuditEntry(
                changeEntry.executionId,
                null,
                changeEntry.changeId,
                changeEntry.author,
                timestamp,
                changeEntry.state.toAuditStatus(),
                changeEntry.type.toAuditType(),
                changeEntry.changeLogClass,
                changeEntry.changeSetMethod,
                changeEntry.executionMillis,
                changeEntry.executionHostname,
                changeEntry.metadata,
                changeEntry.systemChange,
                changeEntry.errorTrace
        );
    }
}
