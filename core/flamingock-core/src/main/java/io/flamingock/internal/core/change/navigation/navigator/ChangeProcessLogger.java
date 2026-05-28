/*
 * Copyright 2023 Flamingock (https://www.flamingock.io)
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
package io.flamingock.internal.core.change.navigation.navigator;

import io.flamingock.internal.common.core.change.TargetSystemDescriptor;
import io.flamingock.internal.common.core.change.ChangeDescriptor;
import io.flamingock.internal.core.change.executable.ExecutableChange;
import io.flamingock.internal.core.change.navigation.step.execution.ExecutionStep;
import io.flamingock.internal.core.change.navigation.step.execution.FailedExecutionStep;
import io.flamingock.internal.core.change.navigation.step.execution.SuccessApplyStep;
import io.flamingock.internal.core.change.navigation.step.rolledback.FailedManualRolledBackStep;
import io.flamingock.internal.core.change.navigation.step.rolledback.ManualRolledBackStep;
import io.flamingock.internal.util.Result;
import io.flamingock.internal.util.log.FlamingockLoggerFactory;
import org.slf4j.Logger;

public class ChangeProcessLogger {
    private static final Logger logger = FlamingockLoggerFactory.getLogger("ChangeExecution");

    private static final String START_DESC = "start";
    private static final String EXECUTION_DESC = "apply";
    private static final String MANUAL_ROLLBACK_DESC = "manual-rollback";
    private static final String AUTO_ROLLBACK_DESC = "auto-rollback";

    public void logStartChangeProcessStrategy(String changeId) {
        logger.debug("Starting change process strategy build [change= {}]", changeId);
    }

    public void logTargetSystemResolved(String changeId, TargetSystemDescriptor targetSystem) {
        String targetSystemId = targetSystem != null ? targetSystem.getId() : null;
        logger.debug("Target system resolved [change= {}, targetSystem= {}]", changeId, targetSystemId);
    }
    
    public void logStrategyApplication(String changeId, String targetSystemId, String strategyType) {
        logger.debug("Executing change [change={} target={} strategy={}]", changeId, targetSystemId, strategyType);
    }

    public void logSkippedExecution(String changeId) {
        logger.info("Change skipped [change={} reason=already_applied]", changeId);
    }

    public void logExecutionResult(ExecutionStep executionStep) {
        String changeId = executionStep.getChange().getId();
        String duration = formatDuration(executionStep.getDuration());

        if (executionStep instanceof SuccessApplyStep) {
            logger.info("Change applied [change={} duration={}]", changeId, duration);
        } else if (executionStep instanceof FailedExecutionStep) {
            FailedExecutionStep failed = (FailedExecutionStep) executionStep;
            logger.error("Change failed [change={} duration={}]: {}",
                        changeId, duration, failed.getMainError().getMessage());
        }
    }

    public void logAutoRollback(ExecutableChange executableChange, long duration) {
        String formattedDuration = formatDuration(duration);
        logger.info("Change rolled back [change={} duration={}]", executableChange.getId(), formattedDuration);
    }

    /**
     * Logs a single WARN line when a failed change cannot be rolled back because it doesn't
     * declare a {@code @RollbackExecution} method. The audit entry stays at {@code FAILED} (no
     * manual-rollback entry follows); manual intervention is surfaced by the execution report
     * separately, so this line stays terse.
     */
    public void logRollbackSkippedNoMethodDeclared(String changeId) {
        logger.warn("Rollback skipped [change={}]: no rollback method provided", changeId);
    }

    public void logManualRollbackResult(ManualRolledBackStep rolledBack) {
        String changeId = rolledBack.getChange().getId();
        String duration = formatDuration(rolledBack.getDuration());

        if (rolledBack instanceof FailedManualRolledBackStep) {
            FailedManualRolledBackStep failed = (FailedManualRolledBackStep) rolledBack;
            logger.error("Rollback failed [change={} duration={}]: {}",
                        changeId, duration, failed.getMainError().getMessage());
        } else {
            logger.info("Change rolled back [change={} duration={}]", changeId, duration);
        }
    }

    public void logAuditResult(Result auditResult, String id, String description) {
        if (auditResult instanceof Result.Error) {
            logger.error("Audit operation failed [change={} operation={}]: {}",
                        id, description, ((Result.Error) auditResult).getError().getMessage());
        } else {
            logger.debug("Audit operation completed [change={} operation={}]", id, description);
        }
    }

    public void logAuditStartResult(Result auditResult, String id) {
        logAuditResult(auditResult, id, START_DESC);
    }

    public void logAuditExecutionResult(Result auditResult, ChangeDescriptor change) {
        logAuditResult(auditResult, change.getId(), EXECUTION_DESC);
    }

    public void logAuditManualRollbackResult(Result auditResult, ChangeDescriptor change) {
        logAuditResult(auditResult, change.getId(), MANUAL_ROLLBACK_DESC);
    }

    public void logAuditAutoRollbackResult(Result auditResult, ChangeDescriptor change) {
        logAuditResult(auditResult, change.getId(), AUTO_ROLLBACK_DESC);
    }
    
    private String formatDuration(long durationMs) {
        if (durationMs < 1000) {
            return durationMs + "ms";
        } else if (durationMs < 60000) {
            return String.format("%.1fs", durationMs / 1000.0);
        } else {
            return String.format("%.1fm", durationMs / 60000.0);
        }
    }
}