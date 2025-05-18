package io.flamingock.common.test.cloud.execution;

import io.flamingock.common.test.cloud.mock.MockRequestResponseTask;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class ExecutionBaseRequestResponseMock {


    private final String executionId;
    private final long acquiredForMillis;
    private final String acquisitionId;
    private final List<MockRequestResponseTask> tasks;

    public ExecutionBaseRequestResponseMock(String executionId,
                                            long acquiredForMillis,
                                            String acquisitionId,
                                            MockRequestResponseTask...tasks) {
        this.executionId = executionId;
        this.acquiredForMillis = acquiredForMillis;
        this.acquisitionId = acquisitionId;
        this.tasks = Arrays.asList(tasks);
    }

    public String getExecutionId() {
        return executionId;
    }

    public long getAcquiredForMillis() {
        return acquiredForMillis;
    }

    public String getAcquisitionId() {
        return acquisitionId;
    }

    public List<MockRequestResponseTask> getTasks() {
        return tasks;
    }

    public Optional<MockRequestResponseTask> getTaskById(String taskId) {
        return tasks.stream()
                .filter(task -> taskId.equals(task.getTaskId()))
                .findFirst();
    }
}
