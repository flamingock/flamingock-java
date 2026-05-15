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

import java.util.Collections;
import java.util.List;

/**
 * One iteration's planner verdict.
 *
 * <ul>
 *   <li>{@link #newExecution(String, Lock, List)} — carries a list of stages to execute under the
 *       given lock. {@link #isExecutionRequired()} reflects whether any of those stages still has
 *       work pending.</li>
 *   <li>{@link #CONTINUE()} — pipeline finished. Successfully. Nothing else to do.</li>
 *   <li>{@link #ABORT()} — stop early. Something went wrong (e.g., an earlier block failed and
 *       its dependents cannot proceed). The operation reads {@link #isAborted()} to break out of
 *       the run loop.</li>
 * </ul>
 *
 * <p>{@code CONTINUE} and {@code ABORT} carry no stages — the run-loop only needs the verdict.
 * The pipeline-level state lives in {@code PipelineRun}; block-aware queries should read
 * {@code PipelineRun.getStageBlocks()} directly.
 */
public class ExecutionPlan implements AutoCloseable {

    public static ExecutionPlan newExecution(String executionId,
                                             Lock lock,
                                             List<ExecutableStage> stages) {
        return new ExecutionPlan(executionId, lock, stages);
    }

    public static ExecutionPlan CONTINUE() {
        return new ExecutionPlan(false, Collections.emptyList());
    }

    public static ExecutionPlan ABORT() {
        return new ExecutionPlan(true, Collections.emptyList());
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

    @Override
    public void close() {
        if (lock != null) {
            lock.release();
        }
    }
}
