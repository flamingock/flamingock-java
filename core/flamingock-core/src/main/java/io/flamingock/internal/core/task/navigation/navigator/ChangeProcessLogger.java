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
package io.flamingock.internal.core.task.navigation.navigator;

import io.flamingock.internal.common.core.task.TargetSystemDescriptor;
import io.flamingock.internal.common.core.task.TaskDescriptor;
import io.flamingock.internal.core.task.executable.ExecutableTask;
import io.flamingock.internal.core.task.navigation.step.execution.ExecutionStep;
import io.flamingock.internal.core.task.navigation.step.execution.FailedExecutionStep;
import io.flamingock.internal.core.task.navigation.step.execution.SuccessExecutionStep;
import io.flamingock.internal.core.task.navigation.step.rolledback.FailedManualRolledBackStep;
import io.flamingock.internal.core.task.navigation.step.rolledback.ManualRolledBackStep;
import io.flamingock.internal.util.Result;
import io.flamingock.internal.util.log.FlamingockLoggerFactory;
import org.slf4j.Logger;

public class ChangeProcessLogger {
    private static final Logger logger = FlamingockLoggerFactory.getLogger("ChangeExecution");

    private static final String START_DESC = "start";
    private static final String EXECUTION_DESC = "execution";
    private static final String MANUAL_ROLLBACK_DESC = "manual-rollback";
    private static final String AUTO_ROLLBACK_DESC = "auto-rollback";

    public void logChangeExecutionStart(String changeId) {
        logger.info("Starting change execution [change={}]", changeId);
    }
    
    public void logTargetSystemResolved(String changeId, TargetSystemDescriptor targetSystem) {
        String targetSystemId = targetSystem != null ? targetSystem.getId() : null;
        logger.debug("Target system resolved [change={}, targetSystem={}]", changeId, targetSystemId);
    }
    
    public void logStrategyApplication(String changeId, String targetSystemId, String strategyType) {
        logger.info("Applying change [change={}, target={}, strategy={}]", changeId, targetSystemId, strategyType);
    }

    public void logSkippedExecution(String changeId) {
        logger.info("SKIPPED [change={}, reason=already applied]", changeId);
    }

    public void logExecutionResult(ExecutionStep executionStep) {
        String taskId = executionStep.getTask().getId();
        String duration = formatDuration(executionStep.getDuration());
        
        if (executionStep instanceof SuccessExecutionStep) {
            logger.info("APPLIED [change={}, duration={}]", taskId, duration);
        } else if (executionStep instanceof FailedExecutionStep) {
            FailedExecutionStep failed = (FailedExecutionStep) executionStep;
            logger.error("FAILED [change={}, duration={}, error={}]", 
                        taskId, duration, failed.getError().getMessage(), failed.getError());
        }
    }

    public void logAutoRollback(ExecutableTask executableChange, long duration) {
        String formattedDuration = formatDuration(duration);
        logger.info("ROLLED_BACK [change={}, duration={}]", executableChange.getId(), formattedDuration);
    }

    public void logManualRollbackResult(ManualRolledBackStep rolledBack) {
        String taskId = rolledBack.getTask().getId();
        String duration = formatDuration(rolledBack.getDuration());
        
        if (rolledBack instanceof FailedManualRolledBackStep) {
            FailedManualRolledBackStep failed = (FailedManualRolledBackStep) rolledBack;
            logger.error("ROLLBACK_FAILED [change={}, duration={}, error={}]", 
                        taskId, duration, failed.getError().getMessage(), failed.getError());
        } else {
            logger.info("ROLLED_BACK [change={}, duration={}]", taskId, duration);
        }
    }

    public void logAuditResult(Result auditResult, String id, String description) {
        if (auditResult instanceof Result.Error) {
            logger.error("Audit operation failed [change={}, operation={}, error={}]", 
                        id, description, ((Result.Error) auditResult).getError().getMessage());
        } else {
            logger.debug("Audit operation completed successfully [change={}, operation={}]", id, description);
        }
    }

    public void logAuditStartResult(Result auditResult, String id) {
        logAuditResult(auditResult, id, START_DESC);
    }

    public void logAuditExecutionResult(Result auditResult, TaskDescriptor change) {
        logAuditResult(auditResult, change.getId(), EXECUTION_DESC);
    }

    public void logAuditManualRollbackResult(Result auditResult, TaskDescriptor change) {
        logAuditResult(auditResult, change.getId(), MANUAL_ROLLBACK_DESC);
    }

    public void logAuditAutoRollbackResult(Result auditResult, TaskDescriptor change) {
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