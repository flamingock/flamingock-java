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
