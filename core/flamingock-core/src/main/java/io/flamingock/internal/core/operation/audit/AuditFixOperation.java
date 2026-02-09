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
package io.flamingock.internal.core.operation.audit;

import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.audit.issue.AuditEntryIssue;
import io.flamingock.internal.common.core.recovery.FixResult;
import io.flamingock.internal.common.core.recovery.Resolution;
import io.flamingock.internal.core.external.store.audit.AuditPersistence;
import io.flamingock.internal.core.operation.Operation;
import io.flamingock.internal.core.plan.ExecutionId;
import io.flamingock.internal.util.StringUtil;

import java.time.LocalDateTime;
import java.util.Optional;

public class AuditFixOperation implements Operation<AuditFixArgs, AuditFixResult> {

    private final AuditPersistence persistence;

    public AuditFixOperation(AuditPersistence persistence) {
        this.persistence = persistence;
    }

    @Override
    public AuditFixResult execute(AuditFixArgs args) {
        Optional<AuditEntryIssue> auditIssue = persistence.getAuditIssueByChangeId(args.getChangeId());
        if (!auditIssue.isPresent()) {
            return new AuditFixResult(args.getChangeId(), args.getResolution(), FixResult.NO_ISSUE_FOUND);
        }

        AuditEntry currentEntry = auditIssue.get().getAuditEntry();
        AuditEntry fixedAuditEntry = new AuditEntry(
                ExecutionId.getNewExecutionId(),
                currentEntry.getStageId(),
                currentEntry.getTaskId(),
                "flamingock-cli",
                LocalDateTime.now(),
                getState(args.getResolution()),
                currentEntry.getType(),
                currentEntry.getClassName(),
                currentEntry.getMethodName(),
                null,
                currentEntry.getExecutionMillis(),
                StringUtil.hostname(),
                currentEntry.getMetadata(),
                currentEntry.getSystemChange(),
                "",
                currentEntry.getTxType(),
                currentEntry.getTargetSystemId(),
                currentEntry.getOrder(),
                currentEntry.getRecoveryStrategy(),
                currentEntry.getTransactionFlag()
        );
        persistence.writeEntry(fixedAuditEntry);
        return new AuditFixResult(args.getChangeId(), args.getResolution(), FixResult.APPLIED);
    }

    private AuditEntry.Status getState(Resolution resolution) {
        if (resolution == Resolution.APPLIED) {
            return AuditEntry.Status.MANUAL_MARKED_AS_APPLIED;
        } else {
            return AuditEntry.Status.MANUAL_MARKED_AS_ROLLED_BACK;
        }
    }
}
