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
package io.flamingock.support.mongock;

import io.flamingock.api.annotations.NonLockGuarded;
import io.flamingock.internal.common.core.pipeline.PipelineHelper;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.audit.AuditHistoryReader;
import io.flamingock.internal.common.core.audit.AuditWriter;
import io.flamingock.internal.common.core.error.FlamingockException;
import io.flamingock.internal.common.core.pipeline.PipelineDescriptor;
import io.flamingock.internal.core.targets.TargetSystemManager;
import io.flamingock.internal.core.targets.operations.TargetSystemOps;
import io.flamingock.internal.core.targets.operations.TransactionalTargetSystemOps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import java.util.List;

import static io.flamingock.internal.common.core.audit.AuditReaderType.MONGOCK;

/**
 * This ChangeUnit is intentionally not annotated with @Change, @Apply, or similar,
 * because it should not be auto-discovered or included in a standard execution stage.
 * Instead, the module injects it programmatically.
 */
public class MongockImportChange {
    private static final Logger logger = LoggerFactory.getLogger("MongockImporter");

    public void importHistory(@Named("change.targetSystem.id") String targetSystemId,
                              @NonLockGuarded TargetSystemManager targetSystemManager,
                              @NonLockGuarded AuditWriter auditWriter,
                              @NonLockGuarded PipelineDescriptor pipelineDescriptor) {
        logger.info("Starting audit log migration from Mongock to Flamingock community audit store");
        AuditHistoryReader legacyHistoryReader = getAuditHistoryReader(targetSystemId, targetSystemManager);
        PipelineHelper pipelineHelper = new PipelineHelper(pipelineDescriptor);
        List<AuditEntry> legacyHistory = legacyHistoryReader.getAuditHistory();
        validate(legacyHistory, targetSystemId);
        legacyHistory.forEach(auditEntryFromOrigin -> {
            //This is the changeId present in the pipeline. If it's a system change or '..._before' won't appear
            AuditEntry auditEntryWithStageId = auditEntryFromOrigin.copyWithNewIdAndStageId(
                    pipelineHelper.getStorableTaskId(auditEntryFromOrigin),
                    pipelineHelper.getStageId(auditEntryFromOrigin));
            auditWriter.writeEntry(auditEntryWithStageId);
        });
    }

    private static AuditHistoryReader getAuditHistoryReader(String targetSystemId, TargetSystemManager targetSystemManager) {
        TargetSystemOps targetSystemOps = targetSystemManager.getTargetSystem(targetSystemId);
        AuditHistoryReader legacyAuditReader;
        if (targetSystemOps instanceof TransactionalTargetSystemOps) {
            legacyAuditReader = ((TransactionalTargetSystemOps) targetSystemOps).getAuditAuditReader(MONGOCK)
                    .orElseThrow(() -> {
                        String message = String.format("TargetSystem[%s], specified in @MongockSupport doesn't provide Mongock importing support", targetSystemId);
                        return new FlamingockException(message);
                    });
        } else {
            String message = "TargetSystem[%s], specified in @MongockSupport must be a TransactionalTargetSystem";
            throw new FlamingockException(message);
        }
        return legacyAuditReader;
    }



    private void validate(List<AuditEntry> legacyHistory, String targetSystemId) {
        if (legacyHistory.isEmpty()) {
            String message = String.format("No audit entries found when importing from '%s'.", targetSystemId);
            throw new FlamingockException(message);
        }
    }
}
