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
import io.flamingock.internal.core.task.executable.ExecutableTask;
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
        return new ExecutionPlan(stages);
    }

    private final String executionId;

    private final Lock lock;

    private final ExecutablePipeline pipeline;

    private ExecutionPlan(List<ExecutableStage> stages) {
        this(null, null, stages);
    }

    private ExecutionPlan(String executionId, Lock lock, List<ExecutableStage> stages) {
        this.executionId = executionId;
        this.lock = lock;
        this.pipeline = new ExecutablePipeline(stages);
    }

    public boolean isExecutionRequired() {
        return pipeline.isExecutionRequired();
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
     * Validates the execution plan for manual intervention requirements.
     * This method analyzes all executable stages and their tasks to identify
     * any that require manual intervention, throwing an exception if found.
     * <p>
     * This centralized validation follows DDD principles by keeping validation
     * logic at the appropriate architectural layer (ExecutionPlan domain).
     * </p>
     * 
     * @throws ManualInterventionRequiredException if any tasks require manual intervention
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
            
            for (ExecutableTask task : stage.getTasks()) {
                if (task.getAction() == ChangeAction.MANUAL_INTERVENTION) {
                    recoveryIssues.add(new RecoveryIssue(task.getId()));
                }
            }
        }
        
        if (!recoveryIssues.isEmpty()) {
            throw new ManualInterventionRequiredException(recoveryIssues, firstStageName);
        }
    }

    @Override
    public void close() {
        if (lock != null) {
            lock.release();
        }
    }
}
