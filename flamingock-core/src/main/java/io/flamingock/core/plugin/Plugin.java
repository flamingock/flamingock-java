package io.flamingock.core.plugin;

import io.flamingock.core.context.ContextInitializable;
import io.flamingock.core.event.EventPublisher;
import io.flamingock.core.context.ContextResolver;
import io.flamingock.core.task.filter.TaskFilter;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public interface Plugin extends ContextInitializable {

    default Optional<EventPublisher> getEventPublisher() {
        return Optional.empty();
    }

    default Optional<ContextResolver> getDependencyContext() {
        return Optional.empty();
    }

    default List<TaskFilter> getTaskFilters() {
        return Collections.emptyList();
    }

}
