package io.flamingock.core.cloud.api.planner.request;


import io.flamingock.core.cloud.api.vo.OngoingStatus;

public class TaskRequest {

    private final String id;

    private final OngoingStatus ongoingStatus;

    private final boolean transactional;

    public static TaskRequest task(String id, boolean transactional) {
        return new TaskRequest(id, OngoingStatus.NONE, transactional);
    }

    public static TaskRequest ongoingExecution(String id, boolean transactional) {
        return new TaskRequest(id, OngoingStatus.EXECUTION, transactional);
    }

    public static TaskRequest ongoingRollback(String id, boolean transactional) {
        return new TaskRequest(id, OngoingStatus.ROLLBACK, transactional);
    }

    public TaskRequest(String id, OngoingStatus ongoingStatus, boolean transactional) {
        this.id = id;
        this.ongoingStatus = ongoingStatus;
        this.transactional = transactional;
    }

    public String getId() {
        return id;
    }

    public OngoingStatus getOngoingStatus() {
        return ongoingStatus;
    }

    public boolean isTransactional() {
        return transactional;
    }
}