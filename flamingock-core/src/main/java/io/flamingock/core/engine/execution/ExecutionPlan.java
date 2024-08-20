/*
 * Copyright 2023 Flamingock (https://oss.flamingock.io)
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

package io.flamingock.core.engine.execution;

import io.flamingock.commons.utils.TriConsumer;
import io.flamingock.core.engine.lock.Lock;
import io.flamingock.core.pipeline.ExecutablePipeline;
import io.flamingock.core.pipeline.ExecutableStage;

import java.util.Collections;
import java.util.List;

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

    @Override
    public void close() {
        if (lock != null) {
            lock.release();
        }
    }
}
