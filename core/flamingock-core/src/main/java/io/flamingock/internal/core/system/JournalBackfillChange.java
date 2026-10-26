/*
 * Copyright 2026 Flamingock (https://www.flamingock.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.flamingock.internal.core.system;

import io.flamingock.api.annotations.NonLockGuarded;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.audit.AuditPersistenceFactory;
import io.flamingock.internal.common.core.error.FlamingockException;
import io.flamingock.internal.common.core.feature.Features;
import io.flamingock.internal.core.external.store.JournalHistoryAppender;
import io.flamingock.internal.util.FeatureFlag;
import io.flamingock.internal.util.Result;
import io.flamingock.internal.util.log.FlamingockLoggerFactory;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;

import static io.flamingock.internal.common.core.pipeline.PipelineHelper.SYSTEM_STAGE_ID;

/**
 * Copies historical audit entries into local journal streams without modifying audit history.
 * Registration and execution order are separate concerns; this change only retains its identity to
 * exclude its own audit records.
 */
public class JournalBackfillChange {
    public static final String CHANGE_ID = "backfill-audit-history-to-journal";

    private static final Logger logger = FlamingockLoggerFactory.getLogger("JournalBackfill");

    private final Map<String, JournalHistoryAppender> journalPersistenceByStage = new HashMap<>();
    private AuditPersistenceFactory<?> persistenceFactory;

    /**
     * Appends every eligible entry in the history reader's order. Persistence instances are reused by
     * stage during execution, including interleaved stages. Once successfully applied, this system change
     * is not executed again. After a failure, a later application startup retries with a new change
     * instance and an empty cache; entries appended before the failure may be duplicated.
     *
     * @param persistenceFactory the audit store's factory, resolved from the runtime context; returned
     *                           stage persistence instances must support {@link JournalHistoryAppender}
     * @throws FlamingockException if journal events are disabled or an append reports an unsuccessful result
     */
    public void journalBackfill(@NonLockGuarded AuditPersistenceFactory<?> persistenceFactory) {
        if (FeatureFlag.isDisabled(Features.JOURNAL_EVENTS)) {
            throw new FlamingockException("Journal backfill requires journal events to be enabled."
                    + " Enable journal events before backfilling audit history.");
        }

        logger.info("Backfilling audit history into journal streams");
        this.persistenceFactory = persistenceFactory;
        for (AuditEntry auditEntry : persistenceFactory.get(SYSTEM_STAGE_ID).getAuditHistory()) {
            if (CHANGE_ID.equals(auditEntry.getChangeId())) {
                continue;
            }
            JournalHistoryAppender persistence = getJournalPersistence(auditEntry);
            Result result = persistence.appendEventFrom(auditEntry);
            if (!(result instanceof Result.Ok)) {
                Throwable cause = result instanceof Result.Error ? ((Result.Error) result).getError() : null;
                throw new FlamingockException("Cannot backfill journal entry " + auditEntry.getChangeId(), cause);
            }
        }
        logger.info("Audit history backfilled into journal streams");
    }

    private JournalHistoryAppender getJournalPersistence(AuditEntry auditEntry) {
        return journalPersistenceByStage.computeIfAbsent(auditEntry.getStageId(),
                stageId -> (JournalHistoryAppender) persistenceFactory.get(stageId));
    }
}
