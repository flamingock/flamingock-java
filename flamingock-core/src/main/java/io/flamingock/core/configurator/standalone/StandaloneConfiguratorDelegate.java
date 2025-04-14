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

package io.flamingock.core.configurator.standalone;

import io.flamingock.core.configurator.ConfigurationDelegate;
import io.flamingock.core.event.model.IPipelineCompletedEvent;
import io.flamingock.core.event.model.IPipelineFailedEvent;
import io.flamingock.core.event.model.IPipelineIgnoredEvent;
import io.flamingock.core.event.model.IPipelineStartedEvent;
import io.flamingock.core.event.model.IStageCompletedEvent;
import io.flamingock.core.event.model.IStageFailedEvent;
import io.flamingock.core.event.model.IStageIgnoredEvent;
import io.flamingock.core.event.model.IStageStartedEvent;
import io.flamingock.core.runtime.dependency.Dependency;
import io.flamingock.core.runtime.dependency.DependencyContext;
import io.flamingock.core.runtime.dependency.DependencyInjectableContext;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class StandaloneConfiguratorDelegate<HOLDER> implements StandaloneConfigurator<HOLDER>, ConfigurationDelegate {

    private final DependencyInjectableContext dependencyManager;
    private final Supplier<HOLDER> holderSupplier;
    private Consumer<IPipelineStartedEvent> pipelineStartedListener;
    private Consumer<IPipelineCompletedEvent> pipelineCompletedListener;
    private Consumer<IPipelineIgnoredEvent> pipelineIgnoredListener;
    private Consumer<IPipelineFailedEvent> pipelineFailedListener;
    private Consumer<IStageStartedEvent> stageStartedListener;
    private Consumer<IStageCompletedEvent> stageCompletedListener;
    private Consumer<IStageIgnoredEvent> stageIgnoredListener;
    private Consumer<IStageFailedEvent> stageFailedListener;

    public StandaloneConfiguratorDelegate(DependencyInjectableContext dependencyManager, Supplier<HOLDER> holderSupplier) {
        this.dependencyManager = dependencyManager;
        this.holderSupplier = holderSupplier;
    }

    @Override
    public DependencyContext getDependencyContext() {
        return dependencyManager;
    }

    @Override
    public HOLDER addDependency(String name, Class<?> type, Object instance) {
        dependencyManager.addDependency(new Dependency(name, type, instance));
        return holderSupplier.get();
    }

    @Override
    public HOLDER addDependency(Object instance) {
        return addDependency(Dependency.DEFAULT_NAME, instance.getClass(), instance);
    }

    @Override
    public HOLDER addDependency(String name, Object instance) {
        return addDependency(name, instance.getClass(), instance);
    }

    @Override
    public HOLDER addDependency(Class<?> type, Object instance) {
        return addDependency(Dependency.DEFAULT_NAME, type, instance);
    }

    @Override
    public HOLDER setPipelineStartedListener(Consumer<IPipelineStartedEvent> listener) {
        this.pipelineStartedListener = listener;
        return holderSupplier.get();
    }

    @Override
    public HOLDER setPipelineCompletedListener(Consumer<IPipelineCompletedEvent> listener) {
        this.pipelineCompletedListener = listener;
        return holderSupplier.get();
    }

    @Override
    public HOLDER setPipelineIgnoredListener(Consumer<IPipelineIgnoredEvent> listener) {
        this.pipelineIgnoredListener = listener;
        return holderSupplier.get();
    }

    @Override
    public HOLDER setPipelineFailedListener(Consumer<IPipelineFailedEvent> listener) {
        this.pipelineFailedListener = listener;
        return holderSupplier.get();
    }

    @Override
    public HOLDER setStageStartedListener(Consumer<IStageStartedEvent> listener) {
        this.stageStartedListener = listener;
        return holderSupplier.get();
    }

    @Override
    public HOLDER setStageCompletedListener(Consumer<IStageCompletedEvent> listener) {
        this.stageCompletedListener = listener;
        return holderSupplier.get();
    }

    @Override
    public HOLDER setStageIgnoredListener(Consumer<IStageIgnoredEvent> listener) {
        this.stageIgnoredListener = listener;
        return holderSupplier.get();
    }

    @Override
    public HOLDER setStageFailedListener(Consumer<IStageFailedEvent> listener) {
        this.stageFailedListener = listener;
        return holderSupplier.get();
    }

    @Override
    public Consumer<IPipelineStartedEvent> getPipelineStartedListener() {
        return pipelineStartedListener;
    }

    @Override
    public Consumer<IPipelineCompletedEvent> getPipelineCompletedListener() {
        return pipelineCompletedListener;
    }

    @Override
    public Consumer<IPipelineIgnoredEvent> getPipelineIgnoredListener() {
        return pipelineIgnoredListener;
    }

    @Override
    public Consumer<IPipelineFailedEvent> getPipelineFailureListener() {
        return pipelineFailedListener;
    }

    @Override
    public Consumer<IStageStartedEvent> getStageStartedListener() {
        return stageStartedListener;
    }

    @Override
    public Consumer<IStageCompletedEvent> getStageCompletedListener() {
        return stageCompletedListener;
    }

    @Override
    public Consumer<IStageIgnoredEvent> getStageIgnoredListener() {
        return stageIgnoredListener;
    }

    @Override
    public Consumer<IStageFailedEvent> getStageFailureListener() {
        return stageFailedListener;
    }

    @Override
    public void initialize() {

    }
}
