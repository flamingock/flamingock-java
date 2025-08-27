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
package io.flamingock.internal.core.plan.community;

import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.audit.AuditTxType;

import io.flamingock.internal.core.pipeline.actions.ChangeAction;
import io.flamingock.internal.core.pipeline.actions.ChangeActionMap;
import io.flamingock.internal.core.recovery.ManualInterventionRequiredException;
import io.flamingock.internal.core.task.loaded.AbstractLoadedTask;
import io.flamingock.internal.util.FlamingockLoggerFactory;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Builds action maps for stages based on audit state information.
 * This component focuses on the decision-making logic for determining
 * what action should be taken for each change based on its audit state
 * and transaction type.
 */
public class CommunityChangeActionBuilder {

    private static final Logger log = FlamingockLoggerFactory.getLogger(CommunityChangeActionBuilder.class);

    /**
     * Builds a stage action plan from audit stage status and validates for execution readiness.
     * This method analyzes each change's audit state and transaction type
     * to determine the appropriate action (APPLY, SKIP, MANUAL_INTERVENTION).
     *
     * @param changeUnits the list of available change units
     * @param auditSnapshot the audit status containing change states and transaction info
     * @return a StageActionPlan with actions for each change
     * @throws ManualInterventionRequiredException
     *         if any changes require manual intervention
     */
    public static ChangeActionMap build(Collection<AbstractLoadedTask> changeUnits, Map<String, AuditEntry> auditSnapshot) {
        Map<String, ChangeAction> actionMap = new HashMap<>();
        for(AbstractLoadedTask changeUnit: changeUnits) {
            AuditEntry entryInfo = auditSnapshot.get(changeUnit.getId());
            ChangeAction action = determineAction(changeUnit, entryInfo);
            actionMap.put(changeUnit.getId(), action);
        }

        return new ChangeActionMap(actionMap);
    }

    /**
     * Determines the action to take based on audit status and transaction type.
     * This implements the recovery decision matrix based on the
     * combination of state and transaction type.
     */
    private static ChangeAction determineAction(AbstractLoadedTask changeUnit, AuditEntry entryInfo) {
        AuditEntry.Status status = entryInfo != null ? entryInfo.getState() : null;
        AuditTxType txType = entryInfo != null ? entryInfo.getTxType() : null;

        if (entryInfo == null || status == null) {
            // No audit entry found - first execution
            log.info("Change[{}] in state='unknown' (TxType={}) -> Action={} | Reason: {}",
                    changeUnit.getId(),
                    txType != null ? txType : "unknown",
                    ChangeAction.APPLY,
                    "No previous audit entry found (first execution)");

            return ChangeAction.APPLY;
        }

        switch (status) {
            case MANUAL_MARKED_AS_EXECUTED:
            case EXECUTED:
                return logDecision(changeUnit, entryInfo, ChangeAction.SKIP, "Change already marked/executed");

            case STARTED:
                if (txType == AuditTxType.TX_SEPARATE_WITH_MARKER) {
                    log.warn("Change[{}] uses TX_SEPARATE_WITH_MARKER, which is not supported in the Community Edition.",
                            changeUnit.getId());
                }
                log.debug("Change[{}] STARTED: recovery.isAlwaysRetry()={}, recovery.strategy={}", 
                    changeUnit.getId(), changeUnit.getRecovery().isAlwaysRetry(), changeUnit.getRecovery().getStrategy());
                if (changeUnit.getRecovery().isAlwaysRetry()) {
                    return logDecision(changeUnit, entryInfo, ChangeAction.APPLY,
                            "Interrupted execution, retry allowed by recovery strategy=" + changeUnit.getRecovery().getStrategy());
                } else {
                    return logDecision(changeUnit, entryInfo, ChangeAction.MANUAL_INTERVENTION,
                            "Interrupted execution, requires manual intervention");
                }

            case MANUAL_MARKED_AS_ROLLED_BACK:
            case EXECUTION_FAILED:
                if (txType == null || txType == AuditTxType.NON_TX) {
                    if (changeUnit.getRecovery().isAlwaysRetry()) {
                        return logDecision(changeUnit, entryInfo, ChangeAction.APPLY,
                                "Failure on NON_TX change, retry allowed by recovery strategy=" + changeUnit.getRecovery().getStrategy());
                    } else {
                        return logDecision(changeUnit, entryInfo, ChangeAction.MANUAL_INTERVENTION,
                                "Failure on NON_TX change, manual intervention required");
                    }
                } else {
                    return logDecision(changeUnit, entryInfo, ChangeAction.APPLY,
                            "Transactional failure, safe to retry");
                }

            case ROLLED_BACK:
                return logDecision(changeUnit, entryInfo, ChangeAction.APPLY,
                        "Previous execution rolled back cleanly, safe to retry");

            case ROLLBACK_FAILED:
                if (changeUnit.getRecovery().isAlwaysRetry()) {
                    return logDecision(changeUnit, entryInfo, ChangeAction.APPLY,
                            "Rollback failed, retry forced by recovery strategy=" + changeUnit.getRecovery().getStrategy());
                } else {
                    return logDecision(changeUnit, entryInfo, ChangeAction.MANUAL_INTERVENTION,
                            "Rollback failed, manual intervention required");
                }

            default:
                if (changeUnit.getRecovery().isAlwaysRetry()) {
                    return logDecision(changeUnit, entryInfo, ChangeAction.APPLY,
                            "Unknown status, retry forced by recovery strategy=" + changeUnit.getRecovery().getStrategy());
                } else {
                    return logDecision(changeUnit, entryInfo, ChangeAction.MANUAL_INTERVENTION,
                            "Unknown status, manual intervention required");
                }
        }
    }

    /**
     * Centralized helper to log consistent decision messages.
     */
    private static ChangeAction logDecision(AbstractLoadedTask changeUnit,
                                            AuditEntry entryInfo,
                                            ChangeAction action,
                                            String reason) {
        AuditEntry.Status status = entryInfo.getState();
        AuditTxType txType = entryInfo.getTxType();

        String msg = String.format("Change[%s] in state='%s' (TxType=%s) -> Action=%s | Reason: %s",
                changeUnit.getId(),
                status,
                txType,
                action,
                reason);

        switch (action) {
            case APPLY:
                log.info(msg);
                break;
            case SKIP:
                log.debug(msg);
                break;
            case MANUAL_INTERVENTION:
                log.error(msg);
                break;
        }
        return action;
    }

}
