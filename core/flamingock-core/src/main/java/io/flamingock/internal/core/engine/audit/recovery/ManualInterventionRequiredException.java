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
package io.flamingock.internal.core.engine.audit.recovery;

import io.flamingock.internal.common.core.error.FlamingockException;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Exception thrown when changes require manual intervention before Flamingock can proceed.
 * This occurs when the audit trail indicates an ambiguous state that requires user verification
 * and explicit resolution through the Flamingock CLI.
 */
public class ManualInterventionRequiredException extends FlamingockException {

    private final List<RecoveryIssue> conflictingChanges;
    private final String stageName;

    /**
     * Creates exception for a single change requiring manual intervention.
     * 
     * @param recoveryIssue the recovery issue for the change
     */
    public ManualInterventionRequiredException(RecoveryIssue recoveryIssue) {
        this(Collections.singletonList(recoveryIssue), "unknown");
    }

    /**
     * Creates exception for multiple changes requiring manual intervention.
     * 
     * @param conflictingChanges the list of recovery issues for conflicting changes
     * @param stageName the name of the stage where the issues occurred
     */
    public ManualInterventionRequiredException(List<RecoveryIssue> conflictingChanges, String stageName) {
        super(buildDetailedMessage(conflictingChanges, stageName));
        this.conflictingChanges = conflictingChanges;
        this.stageName = stageName;
    }

    public List<RecoveryIssue> getConflictingChanges() {
        return conflictingChanges;
    }

    public String getStageName() {
        return stageName;
    }

    /**
     * Builds a detailed error message with user guidance for resolving the conflicts.
     */
    private static String buildDetailedMessage(List<RecoveryIssue> conflictingChanges, String stageName) {
        StringBuilder message = new StringBuilder();
        
        message.append("MANUAL INTERVENTION REQUIRED: Flamingock cannot safely proceed due to ambiguous change states.\n\n");
        
        message.append("The following changes in stage '").append(stageName)
               .append("' require manual verification:\n\n");

        for (RecoveryIssue change : conflictingChanges) {
            message.append("  • Change ID: ").append(change.getChangeId()).append("\n");
            message.append("    Issue: Change requires manual intervention\n");
            message.append("\n");
        }

        message.append("REQUIRED ACTIONS:\n");
        message.append("1. Manually verify the state of your target system(s) for the above changes\n");
        message.append("2. Determine if each change was successfully applied or not\n");
        message.append("3. Use the Flamingock CLI to mark each change as either:\n");
        message.append("   - SUCCESS: If the change was successfully applied to the target system\n");
        message.append("   - FAILED: If the change was not applied or needs to be retried\n");
        message.append("4. Re-run Flamingock after resolving all conflicting states\n\n");

        message.append("CLI Commands:\n");
        for (RecoveryIssue change : conflictingChanges) {
            message.append("  flamingock mark ").append(change.getChangeId()).append(" SUCCESS|FAILED\n");
        }

        message.append("\nThis safety mechanism prevents data corruption by ensuring Flamingock never ");
        message.append("applies changes when the target system state is uncertain.");

        return message.toString();
    }


    /**
     * Returns a summary of all conflicting change IDs for logging purposes.
     * 
     * @return comma-separated list of conflicting change IDs
     */
    public String getConflictingSummary() {
        return conflictingChanges.stream()
                .map(RecoveryIssue::getChangeId)
                .collect(Collectors.joining(", "));
    }
}