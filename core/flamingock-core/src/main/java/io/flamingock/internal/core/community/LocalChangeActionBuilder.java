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
package io.flamingock.internal.core.community;

import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.audit.AuditTxType;
import io.flamingock.internal.core.engine.audit.domain.AuditEntryInfo;
import io.flamingock.internal.core.engine.audit.domain.AuditStageStatus;
import io.flamingock.internal.core.pipeline.actions.ChangeAction;
import io.flamingock.internal.core.pipeline.actions.ChangeActionMap;

import java.util.HashMap;
import java.util.Map;

/**
 * Builds action maps for stages based on audit state information.
 * This component focuses on the decision-making logic for determining
 * what action should be taken for each change based on its audit state
 * and transaction type.
 */
public class LocalChangeActionBuilder {

    /**
     * Builds a stage action plan from audit stage status and validates for execution readiness.
     * This method analyzes each change's audit state and transaction type
     * to determine the appropriate action (APPLY, SKIP, MANUAL_INTERVENTION).
     * 
     * @param auditStatus the audit status containing change states and transaction info
     * @return a StageActionPlan with actions for each change
     * @throws io.flamingock.internal.core.engine.audit.recovery.ManualInterventionRequiredException 
     *         if any changes require manual intervention
     */
    public static ChangeActionMap build(AuditStageStatus auditStatus) {
        Map<String, ChangeAction> actionMap = new HashMap<>();
        
        Map<String, AuditEntryInfo> entryInfoMap = auditStatus.getEntryInfoMap();
        
        for (Map.Entry<String, AuditEntryInfo> entry : entryInfoMap.entrySet()) {
            String changeId = entry.getKey();
            AuditEntryInfo entryInfo = entry.getValue();
            
            ChangeAction action = determineAction(entryInfo.getStatus(), entryInfo.getTxType());
            actionMap.put(changeId, action);
        }
        
        return new ChangeActionMap(actionMap);
    }

    /**
     * Determines the action to take based on audit status and transaction type.
     * This implements the sophisticated recovery decision matrix based on the 
     * combination of state and transaction type:
     * <p>
     * EXECUTED: Always SKIP (already completed successfully)
     * </p>
     * <p>
     * STARTED: Always MANUAL_INTERVENTION (ambiguous state - interrupted execution)
     * - NON_TX: MANUAL_INTERVENTION
     * - TX_SEPARATE_NO_MARKER: MANUAL_INTERVENTION  
     * - TX_SEPARATE: MANUAL_INTERVENTION
     * - TX_SHARED: MANUAL_INTERVENTION (not possible but safe fallback)
     * </p>
     * <p>
     * EXECUTION_FAILED: Depends on transaction type
     * - NON_TX: MANUAL_INTERVENTION (no transaction safety)
     * - TX_SEPARATE_NO_MARKER: APPLY (transactional safety allows retry)
     * - TX_SEPARATE: APPLY (transactional safety allows retry)
     * - TX_SHARED: APPLY (transactional safety allows retry)
     * </p>
     * <p>
     * ROLLED_BACK: Always APPLY (safe to retry after rollback)
     * - All txTypes: APPLY
     * </p>
     * <p>
     * ROLLBACK_FAILED: Always MANUAL_INTERVENTION (unsafe state)
     * - NON_TX: MANUAL_INTERVENTION
     * - TX_SEPARATE_NO_MARKER: MANUAL_INTERVENTION (not possible but safe)
     * - TX_SEPARATE: MANUAL_INTERVENTION (not possible but safe)
     * - TX_SHARED: MANUAL_INTERVENTION (not possible but safe)
     * </p>
     * <p>
     * null (no audit entry): APPLY (first execution)
     * </p>
     * 
     * @param status the audit entry status
     * @param txType the transaction type - critical for recovery decisions
     * @return the action to take for this change
     */
    public static ChangeAction determineAction(AuditEntry.Status status, AuditTxType txType) {
        if (status == null) {
            // No audit entry found - first execution
            return ChangeAction.APPLY;
        }
        
        switch (status) {
            case EXECUTED:
                // Change was successfully executed - skip it
                return ChangeAction.SKIP;
                
            case STARTED:
                // Process was interrupted during execution - ambiguous state
                // Always requires manual intervention regardless of txType
                return ChangeAction.MANUAL_INTERVENTION;
                
            case EXECUTION_FAILED:
                // Decision depends on transaction type
                if (txType == null || txType == AuditTxType.NON_TX) {
                    // Non-transactional changes that failed require manual intervention
                    return ChangeAction.MANUAL_INTERVENTION;
                } else {
                    // Transactional changes can be safely retried after failure
                    return ChangeAction.APPLY;
                }
                
            case ROLLED_BACK:
                // Safe to retry - previous execution was rolled back cleanly
                return ChangeAction.APPLY;
                
            case ROLLBACK_FAILED:
                // Rollback failed - unsafe state requiring manual intervention
                // This should not happen with transactional types, but handle safely
                return ChangeAction.MANUAL_INTERVENTION;
                
            default:
                // Unknown status - require manual intervention for safety
                return ChangeAction.MANUAL_INTERVENTION;
        }
    }

}