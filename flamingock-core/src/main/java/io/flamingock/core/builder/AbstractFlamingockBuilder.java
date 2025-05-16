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

package io.flamingock.core.builder;

import io.flamingock.commons.utils.CollectionUtil;
import io.flamingock.commons.utils.RunnerId;
import io.flamingock.core.api.template.TemplateFactory;
import io.flamingock.core.builder.core.CoreConfiguration;
import io.flamingock.core.builder.core.CoreConfigurator;
import io.flamingock.core.engine.ConnectionEngine;
import io.flamingock.core.event.CompositeEventPublisher;
import io.flamingock.core.event.EventPublisher;
import io.flamingock.core.event.SimpleEventPublisher;
import io.flamingock.core.event.model.IPipelineCompletedEvent;
import io.flamingock.core.event.model.IPipelineFailedEvent;
import io.flamingock.core.event.model.IPipelineIgnoredEvent;
import io.flamingock.core.event.model.IPipelineStartedEvent;
import io.flamingock.core.event.model.IStageCompletedEvent;
import io.flamingock.core.event.model.IStageFailedEvent;
import io.flamingock.core.event.model.IStageIgnoredEvent;
import io.flamingock.core.event.model.IStageStartedEvent;
import io.flamingock.core.pipeline.Pipeline;
import io.flamingock.core.plugin.Plugin;
import io.flamingock.core.plugin.PluginManager;
import io.flamingock.core.runner.PipelineRunnerCreator;
import io.flamingock.core.runner.Runner;
import io.flamingock.core.runner.RunnerBuilder;
import io.flamingock.core.context.Dependency;
import io.flamingock.core.context.ContextResolver;
import io.flamingock.core.context.Context;
import io.flamingock.core.context.PriorityContextResolver;
import io.flamingock.core.system.SystemModuleManager;
import io.flamingock.core.task.filter.TaskFilter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public abstract class AbstractFlamingockBuilder<HOLDER extends AbstractFlamingockBuilder<HOLDER>>
        implements
        CoreConfigurator<HOLDER>,
        ContextConfigurator<HOLDER>,
        RunnerBuilder {
    private static final Logger logger = LoggerFactory.getLogger(AbstractFlamingockBuilder.class);

    private final PluginManager pluginManager;
    private final SystemModuleManager systemModuleManager;
    private final Context dependencyContext;
    protected final CoreConfiguration coreConfiguration;

    private final Driver<?> driver;
    private Consumer<IPipelineStartedEvent> pipelineStartedListener;
    private Consumer<IPipelineCompletedEvent> pipelineCompletedListener;
    private Consumer<IPipelineIgnoredEvent> pipelineIgnoredListener;
    private Consumer<IPipelineFailedEvent> pipelineFailedListener;
    private Consumer<IStageStartedEvent> stageStartedListener;
    private Consumer<IStageCompletedEvent> stageCompletedListener;
    private Consumer<IStageIgnoredEvent> stageIgnoredListener;
    private Consumer<IStageFailedEvent> stageFailedListener;


    ///////////////////////////////////////////////////////////////////////////////////
    //  BUILD

    /// ////////////////////////////////////////////////////////////////////////////////

    protected AbstractFlamingockBuilder(
            CoreConfiguration coreConfiguration,
            Context dependencyContext,
            PluginManager pluginManager,
            SystemModuleManager systemModuleManager,
            Driver<?> driver) {
        this.pluginManager = pluginManager;
        this.systemModuleManager = systemModuleManager;
        this.dependencyContext = dependencyContext;
        this.coreConfiguration = coreConfiguration;
        this.driver = driver;
    }

    protected abstract void doUpdateContext();

    protected abstract HOLDER getSelf();

    @Override
    public final Runner build() {

        TemplateFactory.loadTemplates();

        RunnerId runnerId = RunnerId.generate();
        logger.info("Generated runner id:  {}", runnerId);
        updateContext(runnerId);

        driver.initialize(dependencyContext);
        ConnectionEngine engine = driver.getEngine();
        engine.contributeToSystemModules(systemModuleManager);
        engine.contributeToContext(dependencyContext);

        systemModuleManager.initialize(dependencyContext);
        systemModuleManager.contributeToContext(dependencyContext);

        pluginManager.initialize(dependencyContext);

        Pipeline pipeline = buildPipeline();
        pipeline.contributeToContext(dependencyContext);

        return PipelineRunnerCreator.createWithFinalizer(
                runnerId,
                pipeline,
                engine,
                coreConfiguration,
                buildEventPublisher(),
                buildHierarchicalContext(),
                coreConfiguration.isThrowExceptionIfCannotObtainLock(),
                engine.getCloser()
        );
    }

    private Pipeline buildPipeline() {
        List<TaskFilter> taskFiltersFromPlugins = pluginManager.getPlugins()
                .stream()
                .map(Plugin::getTaskFilters)
                .flatMap(List::stream)
                .collect(Collectors.toList());
        ;
        return Pipeline.builder()
                .addFilters(taskFiltersFromPlugins)
                .addPreviewPipeline(coreConfiguration.getPreviewPipeline())
                .addBeforeUserStages(systemModuleManager.getSortedSystemStagesBefore())
                .addAfterUserStages(systemModuleManager.getSortedSystemStagesAfter())
                .build();
    }

    private void updateContext(RunnerId runnerId) {
        logger.trace("injecting internal configuration");
        addDependency(runnerId);
        addDependency(coreConfiguration);
        doUpdateContext();
    }

    private ContextResolver buildHierarchicalContext() {
        List<ContextResolver> dependencyContextsFromPlugins = pluginManager.getPlugins()
                .stream()
                .map(Plugin::getDependencyContext)
                .flatMap(CollectionUtil::optionalToStream)
                .collect(Collectors.toList());
        return dependencyContextsFromPlugins
                .stream()
                .filter(Objects::nonNull)
                .reduce((previous, current) -> new PriorityContextResolver(current, previous))
                .<ContextResolver>map(accumulated -> new PriorityContextResolver(dependencyContext, accumulated))
                .orElse(dependencyContext);
    }


    @NotNull
    private EventPublisher buildEventPublisher() {

        SimpleEventPublisher simpleEventPublisher = new SimpleEventPublisher()
                //pipeline events
                .addListener(IPipelineStartedEvent.class, getPipelineStartedListener())
                .addListener(IPipelineCompletedEvent.class, getPipelineCompletedListener())
                .addListener(IPipelineIgnoredEvent.class, getPipelineIgnoredListener())
                .addListener(IPipelineFailedEvent.class, getPipelineFailureListener())
                //stage events
                .addListener(IStageStartedEvent.class, getStageStartedListener())
                .addListener(IStageCompletedEvent.class, getStageCompletedListener())
                .addListener(IStageIgnoredEvent.class, getStageIgnoredListener())
                .addListener(IStageFailedEvent.class, getStageFailureListener());
        //TODO this addition is not good, but it will be refactored, once all the builders merged

        List<EventPublisher> eventPublishersFromPlugins = pluginManager.getPlugins()
                .stream()
                .map(Plugin::getEventPublisher)
                .flatMap(CollectionUtil::optionalToStream)
                .collect(Collectors.toList());
        eventPublishersFromPlugins.add(simpleEventPublisher);
        return new CompositeEventPublisher(eventPublishersFromPlugins);
    }

    ///////////////////////////////////////////////////////////////////////////////////
    //  CORE

    /// ////////////////////////////////////////////////////////////////////////////////


    @Override
    public HOLDER setLockAcquiredForMillis(long lockAcquiredForMillis) {
        coreConfiguration.setLockAcquiredForMillis(lockAcquiredForMillis);
        return getSelf();
    }

    @Override
    public HOLDER setLockQuitTryingAfterMillis(Long lockQuitTryingAfterMillis) {
        coreConfiguration.setLockQuitTryingAfterMillis(lockQuitTryingAfterMillis);
        return getSelf();
    }

    @Override
    public HOLDER setLockTryFrequencyMillis(long lockTryFrequencyMillis) {
        coreConfiguration.setLockTryFrequencyMillis(lockTryFrequencyMillis);
        return getSelf();
    }

    @Override
    public HOLDER setThrowExceptionIfCannotObtainLock(boolean throwExceptionIfCannotObtainLock) {
        coreConfiguration.setThrowExceptionIfCannotObtainLock(throwExceptionIfCannotObtainLock);
        return getSelf();
    }

    @Override
    public HOLDER setEnabled(boolean enabled) {
        coreConfiguration.setEnabled(enabled);
        return getSelf();
    }

    @Override
    public HOLDER setStartSystemVersion(String startSystemVersion) {
        coreConfiguration.setStartSystemVersion(startSystemVersion);
        return getSelf();
    }

    @Override
    public HOLDER setEndSystemVersion(String endSystemVersion) {
        coreConfiguration.setEndSystemVersion(endSystemVersion);
        return getSelf();
    }

    @Override
    public HOLDER setServiceIdentifier(String serviceIdentifier) {
        coreConfiguration.setServiceIdentifier(serviceIdentifier);
        return getSelf();
    }

    @Override
    public HOLDER setMetadata(Map<String, Object> metadata) {
        coreConfiguration.setMetadata(metadata);
        return getSelf();
    }

    @Override
    public HOLDER setDefaultAuthor(String publicMigrationAuthor) {
        coreConfiguration.setDefaultAuthor(publicMigrationAuthor);
        return getSelf();
    }

    @Override
    public HOLDER setTransactionStrategy(TransactionStrategy transactionStrategy) {
        coreConfiguration.setTransactionStrategy(transactionStrategy);
        return getSelf();
    }

    @Override
    public long getLockAcquiredForMillis() {
        return coreConfiguration.getLockAcquiredForMillis();
    }

    @Override
    public Long getLockQuitTryingAfterMillis() {
        return coreConfiguration.getLockQuitTryingAfterMillis();
    }

    @Override
    public long getLockTryFrequencyMillis() {
        return coreConfiguration.getLockTryFrequencyMillis();
    }

    @Override
    public boolean isThrowExceptionIfCannotObtainLock() {
        return coreConfiguration.isThrowExceptionIfCannotObtainLock();
    }

    @Override
    public boolean isEnabled() {
        return coreConfiguration.isEnabled();
    }

    @Override
    public String getStartSystemVersion() {
        return coreConfiguration.getStartSystemVersion();
    }

    @Override
    public String getEndSystemVersion() {
        return coreConfiguration.getEndSystemVersion();
    }

    @Override
    public String getServiceIdentifier() {
        return coreConfiguration.getServiceIdentifier();
    }

    @Override
    public Map<String, Object> getMetadata() {
        return coreConfiguration.getMetadata();
    }

    @Override
    public String getDefaultAuthor() {
        return coreConfiguration.getDefaultAuthor();
    }

    @Override
    public TransactionStrategy getTransactionStrategy() {
        return coreConfiguration.getTransactionStrategy();
    }


    @Override
    public HOLDER withImporter(CoreConfiguration.ImporterConfiguration mongockImporterConfiguration) {
        coreConfiguration.setLegacyMongockChangelogSource(mongockImporterConfiguration.getLegacySourceName());
        return getSelf();
    }

    @Override
    public CoreConfiguration.ImporterConfiguration getMongockImporterConfiguration() {
        return coreConfiguration.getMongockImporterConfiguration();
    }

    ///////////////////////////////////////////////////////////////////////////////////
    //  STANDALONE

    /// ////////////////////////////////////////////////////////////////////////////////


    @Override
    public HOLDER addDependency(String name, Class<?> type, Object instance) {
        dependencyContext.addDependency(new Dependency(name, type, instance));
        return getSelf();
    }

    @Override
    public HOLDER addDependency(Object instance) {
        if(instance instanceof Dependency) {
            dependencyContext.addDependency(instance);
            return getSelf();
        } else {
            return addDependency(Dependency.DEFAULT_NAME, instance.getClass(), instance);
        }

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
        return getSelf();
    }

    @Override
    public HOLDER setPipelineCompletedListener(Consumer<IPipelineCompletedEvent> listener) {
        this.pipelineCompletedListener = listener;
        return getSelf();
    }

    @Override
    public HOLDER setPipelineIgnoredListener(Consumer<IPipelineIgnoredEvent> listener) {
        this.pipelineIgnoredListener = listener;
        return getSelf();
    }

    @Override
    public HOLDER setPipelineFailedListener(Consumer<IPipelineFailedEvent> listener) {
        this.pipelineFailedListener = listener;
        return getSelf();
    }

    @Override
    public HOLDER setStageStartedListener(Consumer<IStageStartedEvent> listener) {
        this.stageStartedListener = listener;
        return getSelf();
    }

    @Override
    public HOLDER setStageCompletedListener(Consumer<IStageCompletedEvent> listener) {
        this.stageCompletedListener = listener;
        return getSelf();
    }

    @Override
    public HOLDER setStageIgnoredListener(Consumer<IStageIgnoredEvent> listener) {
        this.stageIgnoredListener = listener;
        return getSelf();
    }

    @Override
    public HOLDER setStageFailedListener(Consumer<IStageFailedEvent> listener) {
        this.stageFailedListener = listener;
        return getSelf();
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
}
