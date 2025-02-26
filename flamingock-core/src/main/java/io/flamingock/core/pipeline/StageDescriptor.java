package io.flamingock.core.pipeline;

import io.flamingock.core.task.descriptor.TaskDescriptor;

import java.util.Collection;

public interface StageDescriptor {

    String getName();

    Collection<TaskDescriptor> getTaskDescriptors();
}
