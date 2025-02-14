package io.flamingock.common.test.cloud.prototype;


import java.util.LinkedList;
import java.util.List;

public class PrototypeStage {

    private final String name;

    private final int order;

    private final List<PrototypeTask> tasks;

    public PrototypeStage(String name, int order) {
        this.name = name;
        this.order = order;
        this.tasks = new LinkedList<>();
    }

    public PrototypeStage addTask(String taskId,
                                  String className,
                                  String methodName,
                                  boolean transactional) {
        tasks.add(new PrototypeTask(taskId, className, methodName, transactional));
        return this;
    }

    public String getName() {
        return name;
    }

    public int getOrder() {
        return order;
    }

    public List<PrototypeTask> getTasks() {
        return tasks;
    }


}
