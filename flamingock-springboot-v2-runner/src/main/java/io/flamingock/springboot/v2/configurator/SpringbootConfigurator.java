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

package io.flamingock.springboot.v2.configurator;



import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;

public interface SpringbootConfigurator<HOLDER> {
  HOLDER setSpringContext(ApplicationContext springContext);

  ApplicationContext getSpringContext();

  HOLDER setEventPublisher(ApplicationEventPublisher applicationEventPublisher);

  ApplicationEventPublisher getEventPublisher();

  HOLDER setRunnerType(SpringRunnerType runnerType);

  SpringRunnerType getRunnerType();
}
