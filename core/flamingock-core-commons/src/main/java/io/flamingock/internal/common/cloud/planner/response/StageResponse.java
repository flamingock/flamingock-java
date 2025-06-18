package io.flamingock.internal.common.cloud.planner.response;

import java.util.List;

public  class StageResponse {
    private String name;

    private int order;

    private List<TaskResponse> tasks;

    public StageResponse() {
    }

    public StageResponse(String name, int order, List<TaskResponse> tasks) {
        this.name = name;
        this.order = order;
        this.tasks = tasks;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<TaskResponse> getTasks() {
        return tasks;
    }

    public void setTasks(List<TaskResponse> tasks) {
        this.tasks = tasks;
    }

    public int getOrder() {
        return order;
    }

    public void setOrder(int order) {
        this.order = order;
    }
}
