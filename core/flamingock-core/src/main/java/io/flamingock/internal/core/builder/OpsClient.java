/*
 * Copyright 2025 Flamingock (https://www.flamingock.io)
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
package io.flamingock.internal.core.builder;

import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.audit.AuditHistoryReader;
import io.flamingock.internal.common.core.audit.AuditIssueResolver;
import io.flamingock.internal.common.core.audit.AuditSnapshotReader;
import io.flamingock.internal.common.core.audit.issue.AuditEntryIssue;
import io.flamingock.internal.common.core.recovery.FixResult;
import io.flamingock.internal.common.core.recovery.Resolution;
import io.flamingock.internal.core.plan.ExecutionId;
import io.flamingock.internal.core.external.store.audit.AuditPersistence;
import io.flamingock.internal.util.StringUtil;
import io.flamingock.internal.util.id.RunnerId;
import io.flamingock.internal.util.log.FlamingockLoggerFactory;
import org.slf4j.Logger;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class OpsClient implements AuditSnapshotReader, AuditHistoryReader, AuditIssueResolver {
    private final Logger logger = FlamingockLoggerFactory.getLogger("OpsClient");

    private final AuditPersistence auditPersistence;

    OpsClient(RunnerId runnerId, AuditPersistence auditPersistence) {
        this.auditPersistence = auditPersistence;
    }

    public List<AuditEntry> getAuditHistory() {
        logger.debug("Getting full audit history");
        return auditPersistence.getAuditHistory();
    }

    @Override
    public List<AuditEntry> getAuditSnapshot() {
        logger.debug("Getting audit entries snapshot (latest per change)");
        return auditPersistence.getAuditSnapshot();
    }

    @Override
    public List<AuditEntry> getAuditSnapshotSince(LocalDateTime since) {
        logger.debug("Getting audit entries since: {}", since);
        return auditPersistence.getAuditSnapshot()
                .stream()
                .filter(auditEntry -> !auditEntry.getCreatedAt().isBefore(since))
                .collect(Collectors.toList());
    }

    @Override
    public Optional<AuditEntryIssue> getAuditIssueByChangeId(String changeId) {
        logger.debug("Getting issue details for changeId: {}", changeId);
        return auditPersistence.getAuditIssueByChangeId(changeId);
    }

    @Override
    public List<AuditEntryIssue> getAuditIssues() {
        logger.debug("Getting audit entries with issues");
        return auditPersistence.getAuditIssues();
    }

    @Override
    // TODO: This needs to be done under the lok
    public FixResult fixAuditIssue(String changeId, Resolution resolution) {
        logger.debug("Change[{}] marked as {}", changeId, resolution);

        Optional<AuditEntryIssue> auditIssue = getAuditIssueByChangeId(changeId);
        if (!auditIssue.isPresent()) {
            return FixResult.NO_ISSUE_FOUND;
        }

        AuditEntry currentEntry = auditIssue.get().getAuditEntry();
        AuditEntry fixedAuditEntry = new AuditEntry(
                ExecutionId.getNewExecutionId(),
                currentEntry.getStageId(),
                currentEntry.getTaskId(),
                "flamingock-cli",//TODO in cloud this will be retrieved from the token
                LocalDateTime.now(),
                getState(resolution),
                currentEntry.getType(),
                currentEntry.getClassName(),
                currentEntry.getMethodName(),
                null, //TODO: set sourceFile
                currentEntry.getExecutionMillis(),
                StringUtil.hostname(),
                currentEntry.getMetadata(),//??
                currentEntry.getSystemChange(),
                currentEntry.isLegacy(),
                "",
                currentEntry.getTxType(),
                currentEntry.getTargetSystemId(),
                currentEntry.getOrder(),
                currentEntry.getRecoveryStrategy(),
                currentEntry.getTransactionFlag()
        );
        auditPersistence.writeEntry(fixedAuditEntry);
        return FixResult.APPLIED;
    }

    private AuditEntry.Status getState(Resolution resolution) {
        if(resolution == Resolution.APPLIED) {
            return AuditEntry.Status.MANUAL_MARKED_AS_APPLIED;
        } else {
            // Resolution.ROLLED_BACK
            return AuditEntry.Status.MANUAL_MARKED_AS_ROLLED_BACK;
        }
    }
}
