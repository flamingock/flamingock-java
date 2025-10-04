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
package io.flamingock.internal.common.core.recovery.action;

import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.audit.AuditTxType;
import io.flamingock.internal.util.log.FlamingockLoggerFactory;
import org.slf4j.Logger;

import static io.flamingock.internal.common.core.recovery.action.ChangeAction.APPLY;
import static io.flamingock.internal.common.core.recovery.action.ChangeAction.MANUAL_INTERVENTION;
import static io.flamingock.internal.common.core.recovery.action.ChangeAction.SKIP;

public final class ChangeActionResolver {
    private static final Logger log = FlamingockLoggerFactory.getLogger(ChangeActionResolver.class);

    private ChangeActionResolver() {
    }

    /**
     * Determines the action to take based on audit status and transaction type.
     * This implements the recovery decision matrix based on the
     * combination of state and transaction type.
     */
    public static ChangeAction resolve(AuditEntry auditEntry) {
        AuditEntry.Status status = auditEntry.getState();
        AuditTxType txStrategy = auditEntry.getTxType();

        switch (status) {
            case MANUAL_MARKED_AS_APPLIED:
            case APPLIED:
                log.debug("Change[{}] in state='{}}' (TxType={}}) -> Action={}} | Reason: {}",
                        auditEntry.getTaskId(), status, txStrategy, SKIP, "Change already marked/applied");
                return SKIP;
            case STARTED:
                if (txStrategy == AuditTxType.TX_SEPARATE_WITH_MARKER) {
                    log.warn("Change[{}] uses TX_SEPARATE_WITH_MARKER, which is not supported in the Community Edition.",
                            auditEntry.getTaskId());
                }
                log.debug("Change[{}] STARTED: recovery.isAlwaysRetry()={}, recovery.strategy={}",
                        auditEntry.getTaskId(), auditEntry.getRecoveryStrategy().isAlwaysRetry(), auditEntry.getRecoveryStrategy());
                if (auditEntry.getRecoveryStrategy().isAlwaysRetry()) {
                    log.debug("Change[{}] in state='{}}' (TxType={}}) -> Action={}} | Reason: {}",
                            auditEntry.getTaskId(), status, txStrategy, APPLY,
                            "Interrupted execution, retry allowed by recovery strategy=" + auditEntry.getRecoveryStrategy());
                    return APPLY;
                } else {
                    log.debug("Change[{}] in state='{}}' (TxType={}}) -> Action={}} | Reason: {}",
                        auditEntry.getTaskId(), status, txStrategy, MANUAL_INTERVENTION,
                            "Interrupted execution, requires manual intervention");
                    return MANUAL_INTERVENTION;
                }

            case FAILED:
                if (txStrategy == null || txStrategy == AuditTxType.NON_TX) {
                    if (auditEntry.getRecoveryStrategy().isAlwaysRetry()) {
                        log.debug("Change[{}] in state='{}}' (TxType={}}) -> Action={}} | Reason: {}",
                                auditEntry.getTaskId(), status, txStrategy, APPLY,
                                "Failure on NON_TX change, retry allowed by recovery strategy=" + auditEntry.getRecoveryStrategy());
                        return APPLY;
                    } else {
                        log.debug("Change[{}] in state='{}}' (TxType={}}) -> Action={}} | Reason: {}",
                                auditEntry.getTaskId(), status, txStrategy, MANUAL_INTERVENTION,
                                "Failure on NON_TX change, manual intervention required");
                        return MANUAL_INTERVENTION;
                    }
                } else {
                    log.debug("Change[{}] in state='{}}' (TxType={}}) -> Action={}} | Reason: {}",
                            auditEntry.getTaskId(), status, txStrategy, APPLY,
                            "Transactional failure, safe to retry");
                    return APPLY;
                }

            case MANUAL_MARKED_AS_ROLLED_BACK:
            case ROLLED_BACK:
                log.debug("Change[{}] in state='{}}' (TxType={}}) -> Action={}} | Reason: {}",
                        auditEntry.getTaskId(), status, txStrategy, APPLY,
                        "Previous execution rolled back cleanly, safe to retry");
                return APPLY;

            case ROLLBACK_FAILED:
                if (auditEntry.getRecoveryStrategy().isAlwaysRetry()) {
                    log.debug("Change[{}] in state='{}}' (TxType={}}) -> Action={}} | Reason: {}",
                            auditEntry.getTaskId(), status, txStrategy, APPLY,
                            "Rollback failed, retry forced by recovery strategy=" + auditEntry.getRecoveryStrategy());
                    return APPLY;
                } else {
                    log.debug("Change[{}] in state='{}}' (TxType={}}) -> Action={}} | Reason: {}",
                            auditEntry.getTaskId(), status, txStrategy,  MANUAL_INTERVENTION,
                            "Rollback failed, manual intervention required");
                    return MANUAL_INTERVENTION;
                }

            default:
                if (auditEntry.getRecoveryStrategy().isAlwaysRetry()) {
                    log.debug("Change[{}] in state='{}}' (TxType={}}) -> Action={}} | Reason: {}",
                            auditEntry.getTaskId(), status, txStrategy, APPLY,
                            "Unknown status, retry forced by recovery strategy=" + auditEntry.getRecoveryStrategy());
                    return APPLY;
                } else {
                    log.debug("Change[{}] in state='{}}' (TxType={}}) -> Action={}} | Reason: {}",
                            auditEntry.getTaskId(), status, txStrategy, MANUAL_INTERVENTION,
                            "Unknown status, manual intervention required");
                    return MANUAL_INTERVENTION;
                }
        }
    }

}
