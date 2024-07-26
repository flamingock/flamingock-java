/*
 * Copyright 2023 Flamingock ("https://oss.flamingock.io")
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.flamingock.core.cloud.utils;

import io.flamingock.core.cloud.transaction.CloudTransactioner;
import io.flamingock.core.cloud.api.transaction.OngoingStatus;
import io.flamingock.core.runtime.dependency.DependencyInjectable;
import io.flamingock.core.task.descriptor.TaskDescriptor;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

public class TestCloudTransactioner implements CloudTransactioner {

    private final HashSet<OngoingStatus> ongoingStatuses;

    public TestCloudTransactioner(OngoingStatus... statuses) {
        ongoingStatuses = statuses != null ? new HashSet<>(Arrays.asList(statuses)) : new HashSet<>();
    }

    @Override
    public Set<OngoingStatus> getOngoingStatuses() {
        return ongoingStatuses;
    }

    @Override
    public void cleanOngoingStatus(String taskId) {
        ongoingStatuses.removeIf(status -> taskId.equals(status.getTaskId()));
    }

    @Override
    public void saveOngoingStatus(OngoingStatus status) {
        ongoingStatuses.add(status);
    }

    @Override
    public <T> T wrapInTransaction(TaskDescriptor taskDescriptor, DependencyInjectable dependencyInjectable, Supplier<T> operation) {
        return operation.get();
    }

    @Override
    public void initialize() {

    }

    @Override
    public void close() {

    }
}
