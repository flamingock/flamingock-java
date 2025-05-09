package io.flamingock.core.builder;

import io.flamingock.core.event.EventPublisher;
import io.flamingock.core.runtime.dependency.DependencyContext;
import io.flamingock.core.task.filter.TaskFilter;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public interface FrameworkPlugin {

    void initialize(DependencyContext dependencyContext);

    default Optional<EventPublisher> getEventPublisher() {
        return Optional.empty();
    }

    default Optional<DependencyContext> getDependencyContext() {
        return Optional.empty();
    }

    default List<TaskFilter> getTaskFilters() {
        return Collections.emptyList();
    }

}
