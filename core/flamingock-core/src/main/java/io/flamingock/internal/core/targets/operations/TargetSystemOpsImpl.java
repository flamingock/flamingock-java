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
package io.flamingock.internal.core.targets.operations;

import io.flamingock.internal.common.core.targets.OperationType;
import io.flamingock.internal.core.runtime.ExecutionRuntime;
import io.flamingock.internal.core.targets.AbstractTargetSystem;

import java.util.function.Function;

public class TargetSystemOpsImpl implements TargetSystemOps {

    private final AbstractTargetSystem<?> targetSystem;


    public TargetSystemOpsImpl(AbstractTargetSystem<?> targetSystem) {
        this.targetSystem = targetSystem;
    }

    @Override
    public OperationType getOperationType() {
        return OperationType.NON_TX;
    }

    @Override
    public final <T> T applyChange(Function<ExecutionRuntime, T> changeApplier, ExecutionRuntime executionRuntime) {
        executionRuntime.addContextLayer(targetSystem.getContext());
        return targetSystem.applyChange(changeApplier, executionRuntime);
    }

    @Override
    public String getId() {
        return targetSystem.getId();
    }

}
