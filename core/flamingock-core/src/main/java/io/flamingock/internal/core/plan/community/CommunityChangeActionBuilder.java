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

import io.flamingock.internal.common.core.recovery.ManualInterventionRequiredException;
import io.flamingock.internal.common.core.recovery.action.ChangeAction;
import io.flamingock.internal.common.core.recovery.action.ChangeActionMap;
import io.flamingock.internal.common.core.recovery.action.ChangeActionResolver;
import io.flamingock.internal.core.task.loaded.AbstractLoadedTask;
import io.flamingock.internal.util.log.FlamingockLoggerFactory;
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
            AuditEntry auditEntry = auditSnapshot.get(changeUnit.getId());
            if (auditEntry == null || auditEntry.getState() == null) {
                // No audit entry found - first execution
                AuditTxType txType = auditEntry != null ? auditEntry.getTxType() : null;
                log.info("Change[{}] in state='unknown' (TxType={}) -> Action={} | Reason: {}",
                        changeUnit.getId(),
                        txType != null ? txType : "unknown",
                        ChangeAction.APPLY,
                        "No previous audit entry found (first execution)");
                actionMap.put(changeUnit.getId(), ChangeAction.APPLY);
            } else {
                ChangeAction action = ChangeActionResolver.resolve(auditEntry);
                actionMap.put(changeUnit.getId(), action);

            }
        }

        return new ChangeActionMap(actionMap);
    }


}
