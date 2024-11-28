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

package io.flamingock.core.cloud.api.planner;

import java.util.List;

public class StageRequest {
    private final String name;

    private final int order;

    private final List<Task> tasks;

    public StageRequest(String name, int order, List<Task> tasks) {
        this.name = name;
        this.order = order;
        this.tasks = tasks;
    }

    public String getName() {
        return name;
    }

    public int getOrder() {
        return order;
    }

    public List<Task> getTasks() {
        return tasks;
    }

    public enum TaskOngoingStatus {
        NONE, EXECUTION, ROLLBACK
    }

    public static class Task {


        private final String id;

        private final TaskOngoingStatus ongoingStatus;

        public static Task task(String id) {
            return new Task(id, TaskOngoingStatus.NONE);
        }

        public static Task ongoingExecution(String id) {
            return new Task(id, TaskOngoingStatus.EXECUTION);
        }

        public static Task ongoingRollback(String id) {
            return new Task(id, TaskOngoingStatus.ROLLBACK);
        }

        private Task(String id, TaskOngoingStatus ongoingStatus) {
            this.id = id;
            this.ongoingStatus = ongoingStatus;
        }

        public String getId() {
            return id;
        }

        public TaskOngoingStatus getOngoingStatus() {
            return ongoingStatus;
        }
    }
}
