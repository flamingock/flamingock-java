package io.flamingock.core.pipeline;

import io.flamingock.core.task.descriptor.LoadedTask;

import java.util.Collection;

public interface StageDescriptor {

    String getName();

    Collection<LoadedTask> getTaskDescriptors();
}
