package io.flamingock.core.pipeline;

import io.flamingock.core.task.descriptor.TaskDescriptor;

import java.util.Optional;

public interface PipelineDescriptor {

    Optional<TaskDescriptor> getTaskDescriptor(String taskId);

    Optional<String> getStageByTask(String taskId);
}
