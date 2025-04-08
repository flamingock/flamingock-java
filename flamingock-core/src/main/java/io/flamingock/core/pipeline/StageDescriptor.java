package io.flamingock.core.pipeline;

import io.flamingock.core.task.TaskDescriptor;

import java.util.Collection;

public interface StageDescriptor {

    String getName();

    Collection<TaskDescriptor> getLoadedTasks();
}
