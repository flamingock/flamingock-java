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

import io.flamingock.internal.common.core.task.TaskDescriptor;
import io.flamingock.internal.core.task.executable.ExecutableTask;
import io.flamingock.internal.core.task.navigation.step.execution.ExecutionStep;
import io.flamingock.internal.core.task.navigation.step.execution.FailedExecutionStep;
import io.flamingock.internal.core.task.navigation.step.execution.SuccessExecutionStep;
import io.flamingock.internal.core.task.navigation.step.rolledback.FailedManualRolledBackStep;
import io.flamingock.internal.core.task.navigation.step.rolledback.ManualRolledBackStep;
import io.flamingock.internal.util.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StepNavigatorLogger {
    private static final Logger logger = LoggerFactory.getLogger("Flamingock-Navigator");

    private static final String START_DESC = "start";
    private static final String EXECUTION_DESC = "execution";
    private static final String MANUAL_ROLLBACK_DESC = "manual-rollback";
    private static final String AUTO_ROLLBACK_DESC = "auto-rollback";

    public void logExecutionStart(ExecutableTask executableChangeUnit) {
        logger.info("Starting {}", executableChangeUnit.getId());
    }

    public void logExecutionResult(ExecutionStep executionStep) {
        String taskId = executionStep.getTask().getId();
        if (executionStep instanceof SuccessExecutionStep) {
            logger.info("change[ {} ] APPLIED[{}] in {}ms ✅", taskId, EXECUTION_DESC, executionStep.getDuration());
        } else if (executionStep instanceof FailedExecutionStep) {
            FailedExecutionStep failed = (FailedExecutionStep) executionStep;
            logger.info("change[ {} ] FAILED[{}] in {}ms ❌", taskId, EXECUTION_DESC, executionStep.getDuration());
            String msg = String.format("error execution task[%s] after %d ms", failed.getTask().getId(), failed.getDuration());
            logger.error(msg, failed.getError());
        }
    }

    public void logAutoRollback(ExecutableTask executableChangeUnit) {
        logger.info("change[ {} ] APPLIED[{}] ✅", executableChangeUnit.getId(), AUTO_ROLLBACK_DESC);
    }

    public void logManualRollbackResult(ManualRolledBackStep rolledBack) {
        if (rolledBack instanceof FailedManualRolledBackStep) {
            logger.info("change[ {} ] FAILED[{}] in {} ms - ❌", rolledBack.getTask().getId(), MANUAL_ROLLBACK_DESC, rolledBack.getDuration());
            String msg = String.format("error rollback task[%s] in %d ms", rolledBack.getTask().getId(), rolledBack.getDuration());
            logger.error(msg, ((FailedManualRolledBackStep) rolledBack).getError());
        } else {
            logger.info("change[ {} ] APPLIED[{}] in {} ms ✅", rolledBack.getTask().getId(), MANUAL_ROLLBACK_DESC, rolledBack.getDuration());
        }
    }

    public void logAuditResult(Result auditResult, String id, String description) {
        if (auditResult instanceof Result.Error) {
            logger.info("change[ {} ] AUDIT FAILED[{}]  ❌ >> {}", id, description, (((Result.Error) auditResult).getError().getLocalizedMessage()));
        } else {
            logger.info("change[ {} ] AUDITED[{}] ✅", id, description);
        }
    }

    public void logAuditStartResult(Result auditResult, String id) {
        logAuditResult(auditResult, id, START_DESC);
    }

    public void logAuditExecutionResult(Result auditResult, TaskDescriptor changeUnit) {
        logAuditResult(auditResult, changeUnit.getId(), EXECUTION_DESC);
    }

    public void logAuditManualRollbackResult(Result auditResult, TaskDescriptor changeUnit) {
        logAuditResult(auditResult, changeUnit.getId(), MANUAL_ROLLBACK_DESC);
    }

    public void logAuditAutoRollbackResult(Result auditResult, TaskDescriptor changeUnit) {
        logAuditResult(auditResult, changeUnit.getId(), AUTO_ROLLBACK_DESC);
    }
}