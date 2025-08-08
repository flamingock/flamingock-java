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
package io.flamingock.core.cloud.utils;

import io.flamingock.internal.common.core.context.ContextResolver;
import io.flamingock.internal.core.cloud.transaction.CloudTransactioner;
import io.flamingock.internal.core.runtime.ExecutionRuntime;
import io.flamingock.internal.core.targets.OngoingTaskStatus;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

public class TestCloudTransactioner implements CloudTransactioner {

    private final HashSet<OngoingTaskStatus> ongoingStatuses;

    public TestCloudTransactioner(OngoingTaskStatus... statuses) {
        ongoingStatuses = statuses != null ? new HashSet<>(Arrays.asList(statuses)) : new HashSet<>();
    }

    @Override
    public Set<OngoingTaskStatus> getAll() {
        return ongoingStatuses;
    }

    @Override
    public void clean(String taskId, ContextResolver contextResolver) {
        ongoingStatuses.removeIf(status -> taskId.equals(status.getTaskId()));
    }

    @Override
    public void register(OngoingTaskStatus status) {
        ongoingStatuses.add(status);
    }

    @Override
    public <T> T wrapInTransaction(ExecutionRuntime executionRuntime, Function<ExecutionRuntime, T> operation) {
        return operation.apply(executionRuntime);
    }

    @Override
    public void initialize() {

    }

    @Override
    public void close() {

    }
}
