package io.flamingock.common.test.mongock;

import io.flamingock.importer.mongock.MongockChangeEntry;
import io.flamingock.importer.mongock.MongockChangeState;
import io.flamingock.importer.mongock.MongockChangeType;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * Helper class for writing MongockChangeEntry objects to MongoDB for testing purposes.
 */
public interface MongockTestHelper {
    /**
     * Writes a MongockChangeEntry to the MongoDB collection.
     *
     * @param entry the entry to write
     */
    void write(MongockChangeEntry entry);

    /**
     * Writes multiple MongockChangeEntry objects to the MongoDB collection.
     *
     * @param entries the entries to write
     * @return the number of documents inserted
     */
    int writeAll(List<MongockChangeEntry> entries);

    default int setupBasicScenario() {
        List<MongockChangeEntry> entries = new ArrayList<>();

        String DEFAULT_EXECUTION_ID = "2025-06-19T06:43:56.656364-778";
        String DEFAULT_HOSTNAME = "Antonios-MacBook-Pro.local";
        SimpleDateFormat DEFAULT_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

        try {
            // System change 00001 before execution
            entries.add(new MongockChangeEntry(
                    DEFAULT_EXECUTION_ID,
                    "system-change-00001_before",
                    "mongock",
                    DEFAULT_DATE_FORMAT.parse("2025-06-19T05:43:57.014Z"),
                    MongockChangeState.EXECUTED,
                    MongockChangeType.BEFORE_EXECUTION,
                    "io.mongock.runner.core.executor.system.changes.SystemChangeUnit00001",
                    "beforeExecution",
                    null,
                    2L,
                    DEFAULT_HOSTNAME,
                    null,
                    true,
                    null
            ));

            // System change 00001 execution
            entries.add(new MongockChangeEntry(
                    DEFAULT_EXECUTION_ID,
                    "system-change-00001",
                    "mongock",
                    DEFAULT_DATE_FORMAT.parse("2025-06-19T05:43:57.034Z"),
                    MongockChangeState.EXECUTED,
                    MongockChangeType.EXECUTION,
                    "io.mongock.runner.core.executor.system.changes.SystemChangeUnit00001",
                    "execution",
                    null,
                    4L,
                    DEFAULT_HOSTNAME,
                    null,
                    true,
                    null
            ));

            // Client initializer before execution
            entries.add(new MongockChangeEntry(
                    DEFAULT_EXECUTION_ID,
                    "client-initializer_before",
                    "mongock",
                    DEFAULT_DATE_FORMAT.parse("2025-06-19T05:43:57.094Z"),
                    MongockChangeState.EXECUTED,
                    MongockChangeType.BEFORE_EXECUTION,
                    "io.mongock.examples.mongodb.standalone.mondogb.sync.migration.initializer.ClientInitializerChangeUnit",
                    "beforeExecution",
                    null,
                    25L,
                    DEFAULT_HOSTNAME,
                    null,
                    false,
                    null
            ));

            // Client initializer execution
            entries.add(new MongockChangeEntry(
                    DEFAULT_EXECUTION_ID,
                    "client-initializer",
                    "mongock",
                    DEFAULT_DATE_FORMAT.parse("2025-06-19T05:43:57.132Z"),
                    MongockChangeState.EXECUTED,
                    MongockChangeType.EXECUTION,
                    "io.mongock.examples.mongodb.standalone.mondogb.sync.migration.initializer.ClientInitializerChangeUnit",
                    "execution",
                    null,
                    23L,
                    DEFAULT_HOSTNAME,
                    null,
                    false,
                    null
            ));

            // Client updater
            entries.add(new MongockChangeEntry(
                    DEFAULT_EXECUTION_ID,
                    "client-updater",
                    "mongock",
                    DEFAULT_DATE_FORMAT.parse("2025-06-19T05:43:57.169Z"),
                    MongockChangeState.EXECUTED,
                    MongockChangeType.EXECUTION,
                    "io.mongock.examples.mongodb.standalone.mondogb.sync.migration.updater.ClientUpdaterChangeUnit",
                    "execution",
                    null,
                    20L,
                    DEFAULT_HOSTNAME,
                    null,
                    false,
                    null
            ));



            // Write all entries to MongoDB
            return writeAll(entries);

        } catch (ParseException e) {
            throw new RuntimeException("Failed to parse date", e);
        }
    }
}
