package io.flamingock.core.pipeline;

import io.flamingock.core.task.descriptor.LoadedTask;

import java.util.Optional;

public interface PipelineDescriptor {

    Optional<LoadedTask> getTaskDescriptor(String taskId);

    Optional<String> getStageByTask(String taskId);
}
