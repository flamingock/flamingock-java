/*
 * Copyright 2025 Flamingock (https://oss.flamingock.io)
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
 
package io.flamingock.common.test.cloud.mock;

import java.util.List;

public class MockRequestResponseExecutionStage {

    private final String stageName;

    private final List<MockRequestResponseTask> tasks;

    public MockRequestResponseExecutionStage(String stageName, List<MockRequestResponseTask> tasks) {
        this.stageName = stageName;
        this.tasks = tasks;
    }

    public String getStageName() {
        return stageName;
    }

    public List<MockRequestResponseTask> getTasks() {
        return tasks;
    }

    public MockRequestResponseTask getTaskById(String taskId) {
        return tasks.stream()
                .filter(task-> taskId.equals(task.getTaskId()))
                .findFirst()
                .orElseThrow(()-> new RuntimeException("Task not found with id: " + taskId));
    }
}
