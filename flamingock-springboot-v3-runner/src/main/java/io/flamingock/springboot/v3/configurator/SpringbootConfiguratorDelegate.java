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

package io.flamingock.springboot.v3.configurator;

import io.flamingock.core.configurator.ConfigurationDelegate;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;

import java.util.function.Supplier;

public class SpringbootConfiguratorDelegate<HOLDER> implements SpringbootConfigurator<HOLDER>, ConfigurationDelegate {

    private final Supplier<HOLDER> holderSupplier;
    private final SpringbootConfiguration springbootConfiguration;
    private ApplicationEventPublisher applicationEventPublisher;
    private ApplicationContext springContext;

    public SpringbootConfiguratorDelegate(SpringbootConfiguration springbootConfiguration, Supplier<HOLDER> holderSupplier) {
        this.springbootConfiguration = springbootConfiguration;
        this.holderSupplier = holderSupplier;
    }

    @Override
    public HOLDER setSpringContext(ApplicationContext springContext) {
        this.springContext = springContext;
        return holderSupplier.get();
    }

    @Override
    public ApplicationContext getSpringContext() {
        return springContext;
    }

    @Override
    public HOLDER setEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
        return holderSupplier.get();
    }

    @Override
    public ApplicationEventPublisher getEventPublisher() {
        return applicationEventPublisher;
    }

    @Override
    public HOLDER setRunnerType(SpringRunnerType runnerType) {
        springbootConfiguration.setRunnerType(runnerType);
        return holderSupplier.get();
    }

    @Override
    public SpringRunnerType getRunnerType() {
        return springbootConfiguration.getRunnerType();
    }

    @Override
    public void initialize() {

    }
}
