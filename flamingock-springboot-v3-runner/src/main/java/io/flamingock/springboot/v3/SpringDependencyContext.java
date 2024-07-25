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

package io.flamingock.springboot.v3;

import io.flamingock.core.runtime.dependency.Dependency;
import io.flamingock.core.runtime.dependency.DependencyContext;
import io.flamingock.core.runtime.dependency.exception.ForbiddenParameterException;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;

import java.util.Optional;

public class SpringDependencyContext implements DependencyContext {


    private final ApplicationContext springContext;

    public SpringDependencyContext(ApplicationContext springContext) {
        this.springContext = springContext;
    }

    @Override
    public Optional<Dependency> getDependency(Class<?> type) throws ForbiddenParameterException {
        try {
            return Optional.of(new Dependency(type, springContext.getBean(type)));
        } catch (BeansException ex) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<Dependency> getDependency(String name) throws ForbiddenParameterException {
        try {
            return Optional.of(new Dependency(springContext.getBean(name)));
        } catch (BeansException ex) {
            return Optional.empty();
        }
    }

}
