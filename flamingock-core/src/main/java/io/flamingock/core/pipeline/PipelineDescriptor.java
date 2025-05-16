package io.flamingock.core.pipeline;

import io.flamingock.core.context.ContextContributor;
import io.flamingock.core.task.TaskDescriptor;

import java.util.Optional;

public interface PipelineDescriptor extends ContextContributor {

    Optional<? extends TaskDescriptor> getLoadedTask(String taskId);

    Optional<String> getStageByTask(String taskId);
}
