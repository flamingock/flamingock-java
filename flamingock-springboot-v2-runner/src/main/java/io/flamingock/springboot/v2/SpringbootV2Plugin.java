package io.flamingock.springboot.v2;

import io.flamingock.core.configurator.FrameworkPlugin;
import io.flamingock.core.event.EventPublisher;
import io.flamingock.core.event.SimpleEventPublisher;
import io.flamingock.core.event.model.IPipelineFailedEvent;
import io.flamingock.core.event.model.IPipelineIgnoredEvent;
import io.flamingock.core.event.model.IPipelineStartedEvent;
import io.flamingock.core.event.model.IStageCompletedEvent;
import io.flamingock.core.event.model.IStageFailedEvent;
import io.flamingock.core.event.model.IStageIgnoredEvent;
import io.flamingock.core.event.model.IStageStartedEvent;
import io.flamingock.core.runtime.dependency.DependencyContext;
import io.flamingock.core.runtime.dependency.DependencyContextWrapper;
import io.flamingock.core.task.filter.TaskFilter;
import io.flamingock.springboot.v2.event.SpringPipelineFailedEvent;
import io.flamingock.springboot.v2.event.SpringPipelineIgnoredEvent;
import io.flamingock.springboot.v2.event.SpringPipelineStartedEvent;
import io.flamingock.springboot.v2.event.SpringStageCompletedEvent;
import io.flamingock.springboot.v2.event.SpringStageFailedEvent;
import io.flamingock.springboot.v2.event.SpringStageIgnoredEvent;
import io.flamingock.springboot.v2.event.SpringStageStartedEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class SpringbootV2Plugin implements FrameworkPlugin {
    
    private ApplicationContext applicationContext;
    private ApplicationEventPublisher eventPublisher;

    @Override
    public void initialize(DependencyContext dependencyContext) {
        if(dependencyContext.getDependency(ApplicationContext.class).isPresent()) {
            applicationContext = (ApplicationContext) dependencyContext
                    .getDependency(ApplicationContext.class)
                    .get()
                    .getInstance();    
        }
        if(dependencyContext.getDependency(ApplicationEventPublisher.class).isPresent()) {
            eventPublisher = (ApplicationEventPublisher) dependencyContext
                    .getDependency(ApplicationContext.class)
                    .get()
                    .getInstance();
        }
    }

    @Override
    public Optional<EventPublisher> getEventPublisher() {
        if(eventPublisher != null) {
            return Optional.of(
                    new SimpleEventPublisher()
                            //pipeline
                            .addListener(IPipelineStartedEvent.class, e -> eventPublisher.publishEvent(new SpringPipelineStartedEvent(this, e)))
                            .addListener(IPipelineIgnoredEvent.class, e -> eventPublisher.publishEvent(new SpringPipelineIgnoredEvent(this, e)))
                            .addListener(IPipelineFailedEvent.class, e -> eventPublisher.publishEvent(new SpringPipelineFailedEvent(this, e)))
                            //stage
                            .addListener(IStageStartedEvent.class, e -> eventPublisher.publishEvent(new SpringStageStartedEvent(this, e)))
                            .addListener(IStageCompletedEvent.class, e -> eventPublisher.publishEvent(new SpringStageCompletedEvent(this, e)))
                            .addListener(IStageIgnoredEvent.class, e -> eventPublisher.publishEvent(new SpringStageIgnoredEvent(this, e)))
                            .addListener(IStageFailedEvent.class, e -> eventPublisher.publishEvent(new SpringStageFailedEvent(this, e)))
            );
        } else {
            return Optional.empty();
        }
    }

    @Override
    public Optional<DependencyContext> getDependencyContext() {
        if(applicationContext != null) {
            DependencyContext springDependencyContext = new DependencyContextWrapper(
                    type -> applicationContext.getBean(type),
                    name -> applicationContext.getBean(name)
            );
            return Optional.of(springDependencyContext);
        } else {
            return Optional.empty();
        }
    }

    @Override
    public List<TaskFilter> getTaskFilters() {
        if(applicationContext != null) {
            String[] activeProfiles = SpringUtil.getActiveProfiles(applicationContext);
            return Collections.singletonList(new SpringbootV2ProfileFilter(activeProfiles));
        } else {
            return Collections.emptyList();
        }
    }
}
