package io.flamingock.internal.common.cloud.planner.response;

public class TaskResponse {
    private String id;
    private RequiredActionTask state;

    public TaskResponse() {
    }

    public TaskResponse(String id, RequiredActionTask state) {
        this.id = id;
        this.state = state;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public RequiredActionTask getState() {
        return state;
    }

    public void setState(RequiredActionTask state) {
        this.state = state;
    }
}
