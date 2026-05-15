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
import io.flamingock.internal.core.pipeline.execution.ExecutableStage;
import io.flamingock.internal.common.core.error.FlamingockException;

import java.util.List;

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

    private final List<ExecutableStage> executableStages;

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
        this.executableStages = stages;
    }

    public boolean isAborted() {
        return aborted;
    }

    public boolean isExecutionRequired() {
        return !aborted && executableStages.stream().anyMatch(ExecutableStage::isExecutionRequired);
    }

    public List<ExecutableStage> getExecutableStages() {
        return executableStages;
    }

    public void applyOnEach(TriConsumer<String, Lock, ExecutableStage> consumer) {
        if (isExecutionRequired()) {
            executableStages.forEach(executableStage -> consumer.accept(executionId, lock, executableStage));
        }
    }

    /**
     * Validates the execution plan. If the planner decided to abort the run for reasons beyond
     * individual change state, throws {@link FlamingockException}.
     *
     * <p>Manual-intervention validation is no longer performed here: it is per-stage and lives in
     * {@code ExecutableStage.validate()}, called inside the operation lambda so a single stage's
     * MI state never aborts the rest of the pipeline.
     */
    public void validate() {
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
