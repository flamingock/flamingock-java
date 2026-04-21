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
package io.flamingock.internal.core.plan;

import io.flamingock.internal.util.TriConsumer;
import io.flamingock.internal.core.external.store.lock.Lock;
import io.flamingock.internal.core.pipeline.execution.ExecutablePipeline;
import io.flamingock.internal.core.pipeline.execution.ExecutableStage;
import io.flamingock.internal.core.change.executable.ExecutableChange;
import io.flamingock.internal.common.core.error.FlamingockException;
import io.flamingock.internal.common.core.recovery.action.ChangeAction;
import io.flamingock.internal.common.core.recovery.ManualInterventionRequiredException;
import io.flamingock.internal.common.core.recovery.RecoveryIssue;

import java.util.List;
import java.util.ArrayList;

public class ExecutionPlan implements AutoCloseable {


    public static ExecutionPlan newExecution(String executionId,
                                             Lock lock,
                                             List<ExecutableStage> stages) {
        return new ExecutionPlan(executionId, lock, stages);
    }

    public static ExecutionPlan CONTINUE(List<ExecutableStage> stages) {
        return new ExecutionPlan(false, stages);
    }

    public static ExecutionPlan ABORT(List<ExecutableStage> stages) {
        return new ExecutionPlan(true, stages);
    }

    private final String executionId;

    private final Lock lock;

    private final ExecutablePipeline pipeline;

    private final boolean aborted;

    private ExecutionPlan(boolean aborted, List<ExecutableStage> stages) {
        this(null, null, aborted, stages);
    }

    private ExecutionPlan(String executionId, Lock lock, List<ExecutableStage> stages) {
        this(executionId, lock, false, stages);
    }

    private ExecutionPlan(String executionId, Lock lock, boolean aborted, List<ExecutableStage> stages) {
        this.executionId = executionId;
        this.lock = lock;
        this.aborted = aborted;
        this.pipeline = new ExecutablePipeline(stages);
    }

    public boolean isAborted() {
        return aborted;
    }

    public boolean isExecutionRequired() {
        return !aborted && pipeline.isExecutionRequired();
    }

    public ExecutablePipeline getPipeline() {
        return pipeline;
    }

    public void applyOnEach(TriConsumer<String, Lock, ExecutableStage> consumer) {
        if (isExecutionRequired()) {
            pipeline.getExecutableStages()
                    .forEach(executableStage -> consumer.accept(executionId, lock, executableStage));
        }
    }

    /**
     * Validates the execution plan.
     * <p>
     * Checks two conditions:
     * <ol>
     *   <li>If any changes require manual intervention, throws {@link ManualInterventionRequiredException}</li>
     *   <li>If the plan is aborted (even without MI changes), throws {@link FlamingockException}
     *       — the execution planner decided to abort for reasons beyond individual change state</li>
     * </ol>
     *
     * @throws ManualInterventionRequiredException if any changes require manual intervention
     * @throws FlamingockException if the plan is aborted without specific MI changes
     */
    public void validate() {
        List<RecoveryIssue> recoveryIssues = new ArrayList<>();
        String firstStageName = "unknown";
        boolean hasStages = false;

        for (ExecutableStage stage : pipeline.getExecutableStages()) {
            if (!hasStages) {
                firstStageName = stage.getName();
                hasStages = true;
            }

            for (ExecutableChange change : stage.getChanges()) {
                if (change.getAction() == ChangeAction.MANUAL_INTERVENTION) {
                    recoveryIssues.add(new RecoveryIssue(change.getId()));
                }
            }
        }

        if (!recoveryIssues.isEmpty()) {
            throw new ManualInterventionRequiredException(recoveryIssues, firstStageName);
        }

        if (aborted) {
            throw new FlamingockException("Execution aborted by the execution planner");
        }
    }

    @Override
    public void close() {
        if (lock != null) {
            lock.release();
        }
    }
}
