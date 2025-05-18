package io.flamingock.common.test.cloud.mock;

import io.flamingock.core.cloud.api.audit.AuditEntryRequest;
import io.flamingock.core.cloud.api.planner.response.RequiredActionTask;
import io.flamingock.core.cloud.api.vo.OngoingStatus;

import java.util.Arrays;
import java.util.List;

public class MockRequestResponseTask {
    private final String taskId;
    private final OngoingStatus ongoingStatus;
    private final RequiredActionTask requiredAction;


    public MockRequestResponseTask(String taskId,
                                   OngoingStatus ongoingStatus) {
        this(taskId, ongoingStatus,  RequiredActionTask.PENDING_EXECUTION);
    }

    public MockRequestResponseTask(String taskId,
                                   RequiredActionTask requiredAction) {
        this(taskId, OngoingStatus.NONE, requiredAction);
    }

    public MockRequestResponseTask(String taskId,
                                   OngoingStatus ongoingStatus,
                                   RequiredActionTask requiredAction) {
        this.taskId = taskId;
        this.ongoingStatus = ongoingStatus;
        this.requiredAction = requiredAction;
    }

    public String getTaskId() {
        return taskId;
    }

    public OngoingStatus getOngoingStatus() {
        return ongoingStatus;
    }

    public RequiredActionTask getRequiredAction() {
        return requiredAction;
    }

}
