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
package io.flamingock.internal.core.targets;

import io.flamingock.internal.common.cloud.vo.OngoingStatus;
import io.flamingock.internal.common.core.context.ContextResolver;
import io.flamingock.internal.core.task.executable.ExecutableTask;
import java.util.Set;

/**
 * Repository responsible for persisting and retrieving the execution status of tasks
 * that are currently in progress or have been interrupted.
 * <p>
 * These statuses are used during synchronization with the server to determine
 * whether tasks need to be re-executed, rolled back, or skipped.
 */
public interface OngoingTaskStatusRepository {

    /**
     * Retrieves all ongoing task statuses currently stored in the database.
     * <p>
     * This is typically used during initialization or synchronization with the server,
     * to determine the state of previously interrupted or pending tasks.
     *
     * @return a non-null set of {@link OngoingTaskStatus}, possibly empty
     */
    Set<OngoingTaskStatus> getAll();

    /**
     * Removes the ongoing status associated with the given task ID.
     * This operation needs to participate in the ongoing transaction
     * <p>
     * This operation is idempotent: if no status exists for the given task ID, nothing happens.
     *
     * @param taskId the ID of the task to clean
     * @param contextResolver the context to retrieve the transactional session
     * @throws RuntimeException if the operation fails (e.g., storage unavailable)
     */
    void clean(String taskId, ContextResolver contextResolver);

    /**
     * Registers an ongoing task status by upserting it into the local database.
     * <p>
     * If a status already exists for the same {@code taskId}, it will be overwritten.
     * This operation is idempotent and is used to persist the task's latest known state.
     *
     * @param status the status to register
     */
    void register(OngoingTaskStatus status);

    /**
     * Registers the given task as currently executing.
     * <p>
     * Internally, this calls {@link #register(OngoingTaskStatus)} with an {@link OngoingStatus#EXECUTION} value.
     *
     * @param ongoingTask the task to mark as executing
     */
    default void registerAsExecuting(ExecutableTask ongoingTask) {
        register(new OngoingTaskStatus(ongoingTask.getId(), OngoingStatus.EXECUTION));
    }

    /**
     * Registers the given task as currently rolling back.
     * <p>
     * Internally, this calls {@link #register(OngoingTaskStatus)} with an {@link OngoingStatus#ROLLBACK} value.
     *
     * @param ongoingTask the task to mark as rolling back
     */
    default void registerAsRollingBack(ExecutableTask ongoingTask) {
        register(new OngoingTaskStatus(ongoingTask.getId(), OngoingStatus.ROLLBACK));
    }
}
