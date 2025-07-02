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

package io.flamingock.internal.core.builder;

import io.flamingock.internal.util.CollectionUtil;
import io.flamingock.internal.util.Property;
import io.flamingock.internal.util.id.RunnerId;
import io.flamingock.internal.common.core.template.ChangeTemplateManager;
import io.flamingock.internal.core.builder.core.CoreConfiguration;
import io.flamingock.internal.core.builder.core.CoreConfigurator;
import io.flamingock.internal.core.engine.ConnectionEngine;
import io.flamingock.internal.core.event.CompositeEventPublisher;
import io.flamingock.internal.core.event.EventPublisher;
import io.flamingock.internal.core.event.SimpleEventPublisher;
import io.flamingock.internal.core.event.model.IPipelineCompletedEvent;
import io.flamingock.internal.core.event.model.IPipelineFailedEvent;
import io.flamingock.internal.core.event.model.IPipelineIgnoredEvent;
import io.flamingock.internal.core.event.model.IPipelineStartedEvent;
import io.flamingock.internal.core.event.model.IStageCompletedEvent;
import io.flamingock.internal.core.event.model.IStageFailedEvent;
import io.flamingock.internal.core.event.model.IStageIgnoredEvent;
import io.flamingock.internal.core.event.model.IStageStartedEvent;
import io.flamingock.internal.core.pipeline.loaded.LoadedPipeline;
import io.flamingock.internal.core.plugin.Plugin;
import io.flamingock.internal.core.plugin.PluginManager;
import io.flamingock.internal.core.runner.PipelineRunnerCreator;
import io.flamingock.internal.core.runner.Runner;
import io.flamingock.internal.core.runner.RunnerBuilder;
import io.flamingock.internal.common.core.context.Dependency;
import io.flamingock.internal.common.core.context.ContextResolver;
import io.flamingock.internal.common.core.context.Context;
import io.flamingock.internal.core.context.PriorityContextResolver;
import io.flamingock.internal.common.core.system.SystemModuleManager;
import io.flamingock.internal.core.task.filter.TaskFilter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.Period;
import java.time.ZonedDateTime;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
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
    private final Context context;
    private final CoreConfiguration coreConfiguration;

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
            Context context,
            PluginManager pluginManager,
            SystemModuleManager systemModuleManager,
            Driver<?> driver) {
        this.pluginManager = pluginManager;
        this.systemModuleManager = systemModuleManager;
        this.context = context;
        this.coreConfiguration = coreConfiguration;
        this.driver = driver;
    }

    protected abstract void doUpdateContext();

    protected abstract HOLDER getSelf();

    @Override
    public final Runner build() {

        ChangeTemplateManager.loadTemplates();

        RunnerId runnerId = RunnerId.generate();
        logger.info("Generated runner id:  {}", runnerId);
        updateContext(runnerId);

        driver.initialize(context);
        ConnectionEngine engine = driver.getEngine();
        engine.contributeToSystemModules(systemModuleManager);
        engine.contributeToContext(context);

        systemModuleManager.initialize(context);
        systemModuleManager.contributeToContext(context);

        pluginManager.initialize(context);

        LoadedPipeline pipeline = buildPipeline();
        pipeline.contributeToContext(context);

        return PipelineRunnerCreator.createWithFinalizer(
                runnerId,
                pipeline,
                engine,
                coreConfiguration,
                buildEventPublisher(),
                buildHierarchicalContext(),
                engine.getNonGuardedTypes(),
                coreConfiguration.isThrowExceptionIfCannotObtainLock(),
                engine.getCloser()
        );
    }

    private LoadedPipeline buildPipeline() {
        List<TaskFilter> taskFiltersFromPlugins = pluginManager.getPlugins()
                .stream()
                .map(Plugin::getTaskFilters)
                .flatMap(List::stream)
                .collect(Collectors.toList());
        ;
        return LoadedPipeline.builder()
                .addFilters(taskFiltersFromPlugins)
                .addPreviewPipeline(coreConfiguration.getPreviewPipeline())
                .addBeforeUserStages(systemModuleManager.getSortedSystemStagesBefore())
                .addAfterUserStages(systemModuleManager.getSortedSystemStagesAfter())
                .build();
    }

    private void updateContext(RunnerId runnerId) {
        logger.trace("injecting internal configuration");
        setProperty(runnerId);
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
                .<ContextResolver>map(accumulated -> new PriorityContextResolver(context, accumulated))
                .orElse(context);
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
        context.addDependency(new Dependency(name, type, instance));
        return getSelf();
    }

    @Override
    public HOLDER addDependency(Object instance) {
        if(instance instanceof Dependency) {
            context.addDependency(instance);
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


    @Override
    public HOLDER setProperty(Property property) {
        context.setProperty(property);
        return getSelf();
    }

    @Override
    public HOLDER setProperty(String key, String value) {
        context.setProperty(key, value);
        return getSelf();
    }

    @Override
    public HOLDER setProperty(String key, Boolean value) {
        context.setProperty(key, value);
        return getSelf();
    }

    @Override
    public HOLDER setProperty(String key, Integer value) {
        context.setProperty(key, value);
        return getSelf();
    }

    @Override
    public HOLDER setProperty(String key, Float value) {
        context.setProperty(key, value);
        return getSelf();
    }

    @Override
    public HOLDER setProperty(String key, Long value) {
        context.setProperty(key, value);
        return getSelf();
    }

    @Override
    public HOLDER setProperty(String key, Double value) {
        context.setProperty(key, value);
        return getSelf();
    }

    @Override
    public HOLDER setProperty(String key, UUID value) {
        context.setProperty(key, value);
        return getSelf();
    }

    @Override
    public HOLDER setProperty(String key, Currency value) {
        context.setProperty(key, value);
        return getSelf();
    }

    @Override
    public HOLDER setProperty(String key, Locale value) {
        context.setProperty(key, value);
        return getSelf();
    }

    @Override
    public HOLDER setProperty(String key, Charset value) {
        context.setProperty(key, value);
        return getSelf();
    }

    @Override
    public HOLDER setProperty(String key, File value) {
        context.setProperty(key, value);
        return getSelf();
    }

    @Override
    public HOLDER setProperty(String key, Path value) {
        context.setProperty(key, value);
        return getSelf();
    }

    @Override
    public HOLDER setProperty(String key, InetAddress value) {
        context.setProperty(key, value);
        return getSelf();
    }

    @Override
    public HOLDER setProperty(String key, URL value) {
        context.setProperty(key, value);
        return getSelf();
    }

    @Override
    public HOLDER setProperty(String key, URI value) {
        context.setProperty(key, value);
        return getSelf();
    }

    @Override
    public HOLDER setProperty(String key, Duration value) {
        context.setProperty(key, value);
        return getSelf();
    }

    @Override
    public HOLDER setProperty(String key, Period value) {
        context.setProperty(key, value);
        return getSelf();
    }

    @Override
    public HOLDER setProperty(String key, Instant value) {
        context.setProperty(key, value);
        return getSelf();
    }

    @Override
    public HOLDER setProperty(String key, LocalDate value) {
        context.setProperty(key, value);
        return getSelf();
    }

    @Override
    public HOLDER setProperty(String key, LocalTime value) {
        context.setProperty(key, value);
        return getSelf();
    }

    @Override
    public HOLDER setProperty(String key, LocalDateTime value) {
        context.setProperty(key, value);
        return getSelf();
    }

    @Override
    public HOLDER setProperty(String key, ZonedDateTime value) {
        context.setProperty(key, value);
        return getSelf();
    }

    @Override
    public HOLDER setProperty(String key, OffsetDateTime value) {
        context.setProperty(key, value);
        return getSelf();
    }

    @Override
    public HOLDER setProperty(String key, OffsetTime value) {
        context.setProperty(key, value);
        return getSelf();
    }

    @Override
    public HOLDER setProperty(String key, java.util.Date value) {
        context.setProperty(key, value);
        return getSelf();
    }

    @Override
    public HOLDER setProperty(String key, java.sql.Date value) {
        context.setProperty(key, value);
        return getSelf();
    }

    @Override
    public HOLDER setProperty(String key, Time value) {
        context.setProperty(key, value);
        return getSelf();
    }

    @Override
    public HOLDER setProperty(String key, Timestamp value) {
        context.setProperty(key, value);
        return getSelf();
    }

    @Override
    public HOLDER setProperty(String key, String[] value) {
        context.setProperty(key, value);
        return getSelf();
    }

    @Override
    public HOLDER setProperty(String key, Integer[] value) {
        context.setProperty(key, value);
        return getSelf();
    }

    @Override
    public HOLDER setProperty(String key, Long[] value) {
        context.setProperty(key, value);
        return getSelf();
    }

    @Override
    public HOLDER setProperty(String key, Double[] value) {
        context.setProperty(key, value);
        return getSelf();
    }

    @Override
    public HOLDER setProperty(String key, Float[] value) {
        context.setProperty(key, value);
        return getSelf();
    }

    @Override
    public HOLDER setProperty(String key, Boolean[] value) {
        context.setProperty(key, value);
        return getSelf();
    }

    @Override
    public HOLDER setProperty(String key, Byte[] value) {
        context.setProperty(key, value);
        return getSelf();
    }

    @Override
    public HOLDER setProperty(String key, Short[] value) {
        context.setProperty(key, value);
        return getSelf();
    }

    @Override
    public HOLDER setProperty(String key, Character[] value) {
        context.setProperty(key, value);
        return getSelf();
    }

    @Override
    public <T extends Enum<T>> HOLDER setProperty(String key, T value) {
        context.setProperty(key, value);
        return getSelf();
    }

}
