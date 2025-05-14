/*
 * Copyright 2023 Flamingock (https://oss.flamingock.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.flamingock.springboot.v2;

import io.flamingock.core.builder.FrameworkPlugin;
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
        return applicationContext != null
                ? Optional.of(new SpringbootV2DependencyContext(applicationContext))
                : Optional.empty();
    }

    @Override
    public List<TaskFilter> getTaskFilters() {
        if(applicationContext != null) {
            String[] activeProfiles = SpringbootUtil.getActiveProfiles(applicationContext);
            return Collections.singletonList(new SpringbootV2ProfileFilter(activeProfiles));
        } else {
            return Collections.emptyList();
        }
    }
}
