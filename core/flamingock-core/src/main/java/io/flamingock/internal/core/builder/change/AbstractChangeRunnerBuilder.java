/*
 * Copyright 2023 Flamingock (https://www.flamingock.io)
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
package io.flamingock.internal.core.builder.change;

import io.flamingock.api.targets.TargetSystem;
import io.flamingock.internal.common.core.context.Context;
import io.flamingock.internal.common.core.context.ContextInjectable;
import io.flamingock.internal.common.core.context.ContextResolver;
import io.flamingock.internal.common.core.context.Dependency;
import io.flamingock.internal.common.core.template.ChangeTemplateManager;
import io.flamingock.internal.core.builder.AbstractBuilder;
import io.flamingock.internal.core.builder.BuilderException;
import io.flamingock.internal.core.configuration.EventLifecycleConfigurator;
import io.flamingock.internal.core.configuration.core.CoreConfiguration;
import io.flamingock.internal.core.configuration.core.CoreConfigurator;
import io.flamingock.internal.common.core.context.ContextConfigurable;
import io.flamingock.internal.core.context.PriorityContext;
import io.flamingock.internal.core.context.PriorityContextResolver;
import io.flamingock.internal.core.context.SimpleContext;
import io.flamingock.internal.core.store.AuditStore;
import io.flamingock.internal.core.store.audit.AuditPersistence;
import io.flamingock.internal.core.store.audit.LifecycleAuditWriter;
import io.flamingock.internal.core.plan.ExecutionPlanner;
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
import io.flamingock.internal.core.targets.TargetSystemManager;
import io.flamingock.internal.core.task.filter.TaskFilter;
import io.flamingock.internal.util.CollectionUtil;
import io.flamingock.internal.util.Property;
import io.flamingock.internal.util.id.RunnerId;
import org.jetbrains.annotations.NotNull;
import io.flamingock.internal.util.FlamingockLoggerFactory;
import org.slf4j.Logger;

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

public abstract class AbstractChangeRunnerBuilder<HOLDER extends AbstractChangeRunnerBuilder<HOLDER>>
        extends AbstractBuilder<HOLDER>
        implements
        EventLifecycleConfigurator<HOLDER>,
        RunnerBuilder {
    private static final Logger logger = FlamingockLoggerFactory.getLogger("Builder");

    protected final PluginManager pluginManager;

    private final TargetSystemManager targetSystemManager = new TargetSystemManager();
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
    protected AbstractChangeRunnerBuilder(
            CoreConfiguration coreConfiguration,
            Context context,
            PluginManager pluginManager) {
        this(coreConfiguration, context, pluginManager, null);
    }

    protected AbstractChangeRunnerBuilder(
            CoreConfiguration coreConfiguration,
            Context context,
            PluginManager pluginManager,
            AuditStore<?> auditStore) {
        super(coreConfiguration, context, auditStore);
        this.pluginManager = pluginManager;

    }

    protected abstract void updateContextSpecific();

    protected abstract ExecutionPlanner buildExecutionPlanner(RunnerId runnerId);

    protected abstract HOLDER getSelf();

    /**
     * Builds and returns a configured Flamingock Runner ready for execution.
     *
     * <p>This is the central orchestration method that assembles all components into a functional
     * runner. The execution order is critical due to hierarchical dependency resolution - components
     * must be initialized in the correct sequence to ensure all dependencies are available when needed.
     *
     * <h3>Execution Flow Overview:</h3>
     * <ol>
     * <li><strong>Template Loading</strong> - Loads change templates for no-code migrations</li>
     * <li><strong>Context Preparation</strong> - Sets up base context with runner ID and core config</li>
     * <li><strong>Plugin Initialization</strong> - Initializes plugins with access to base context</li>
     * <li><strong>Hierarchical Context Building</strong> - Merges external contexts (e.g., Spring Boot)</li>
     * <li><strong>AuditStore Initialization</strong> - Initializes driver with full hierarchical context</li>
     * <li><strong>Persistence Setup</strong> - Retrieves AuditPersistence and registers audit writer</li>
     * <li><strong>Pipeline Building</strong> - Constructs pipeline and contributes dependencies</li>
     * <li><strong>Runner Creation</strong> - Assembles final runner with all components</li>
     * </ol>
     *
     * <h3>Critical Order Dependencies:</h3>
     * <p><strong>Hierarchical Context MUST be built before AuditStore initialization.</strong>
     * The hierarchical context merges external dependency sources (like Spring Boot's application context)
     * with Flamingock's internal context. When {@code driver.initialize(hierarchicalContext)} is called,
     * the driver searches this context for required dependencies (database connections, configuration, etc.).
     * If the hierarchical context is built after driver initialization, these external dependencies
     * won't be available, causing the AuditPersistence to fail during execution.
     *
     * <h3>Component Relationships:</h3>
     * <ul>
     * <li><strong>AuditStore → Engine</strong>: AuditStore provides the ConnectionEngine implementation</li>
     * <li><strong>Engine → AuditWriter</strong>: Engine provides audit writer for execution tracking</li>
     * <li><strong>Pipeline → Context</strong>: Pipeline contributes additional dependencies back to context</li>
     * <li><strong>HierarchicalContext → All Components</strong>: Provides unified dependency resolution</li>
     * </ul>
     *
     * <h3>Integration Points:</h3>
     * <ul>
     * <li><strong>Plugins</strong>: External context merged via {@code buildHierarchicalContext()}</li>
     * <li><strong>Plugins</strong>: Contribute task filters and event publishers</li>
     * <li><strong>Templates</strong>: Loaded for YAML-based pipeline definitions</li>
     * </ul>
     *
     * @return A fully configured Runner ready for execution
     * @see #buildContext() for context merging details
     * @see AuditStore#initialize(ContextResolver) for driver initialization requirements
     * @see LoadedPipeline#contributeToContext(ContextInjectable) for pipeline contributions
     */
    @Override
    public final Runner build() {

        ChangeTemplateManager.loadTemplates();
        pluginManager.initialize(context);

        validateAuditStore();

        RunnerId runnerId = generateRunnerId();

        PriorityContext hierarchicalContext = buildContext();

        auditStore.initialize(hierarchicalContext);

        //Handles the TargetSystems
        targetSystemManager.setAuditStoreTargetSystem(auditStore.getTargetSystem());
        targetSystemManager.initialize(hierarchicalContext);

        //Configure the persistence from the auditStore
        AuditPersistence persistence = getAuditPersistence(hierarchicalContext);

        //Loads the pipeline
        //This contribution to the context is fine after components initialization as it's only used
        LoadedPipeline pipeline = loadPipeline();
        pipeline.contributeToContext(hierarchicalContext);




        return PipelineRunnerCreator.create(
                runnerId,
                pipeline,
                persistence,
                buildExecutionPlanner(runnerId),
                targetSystemManager,
                coreConfiguration,
                buildEventPublisher(),
                hierarchicalContext,
                persistence.getNonGuardedTypes(),
                coreConfiguration.isThrowExceptionIfCannotObtainLock(),
                coreConfiguration.isRelaxTargetSystemValidation(),
                persistence.getCloser()
        );
    }


    private LoadedPipeline loadPipeline() {
        List<TaskFilter> taskFiltersFromPlugins = pluginManager.getPlugins()
                .stream()
                .map(Plugin::getTaskFilters)
                .flatMap(List::stream)
                .collect(Collectors.toList());

        return LoadedPipeline.builder()
                .addFilters(taskFiltersFromPlugins)
                .addPreviewPipeline(coreConfiguration.getPreviewPipeline())
                .build();
    }

    private PriorityContext buildContext() {
        logger.trace("injecting internal configuration");
        addDependency(coreConfiguration);
        updateContextSpecific();
        List<ContextResolver> dependencyContextsFromPlugins = pluginManager.getPlugins()
                .stream()
                .map(Plugin::getDependencyContext)
                .flatMap(CollectionUtil::optionalToStream)
                .collect(Collectors.toList());
        return dependencyContextsFromPlugins
                .stream()
                .filter(Objects::nonNull)
                .reduce((previous, current) -> new PriorityContextResolver(current, previous))
                .map(accumulated -> new PriorityContext(context, accumulated))
                .orElse(new PriorityContext(new SimpleContext(), context));
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
    public HOLDER addTargetSystem(TargetSystem targetSystem) {
        targetSystemManager.add(targetSystem);
        return getSelf();
    }

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
    public HOLDER setRelaxTargetSystemValidation(boolean relaxTargetSystemValidation) {
        coreConfiguration.setRelaxTargetSystemValidation(relaxTargetSystemValidation);
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
    public boolean isRelaxTargetSystemValidation() {
        return coreConfiguration.isRelaxTargetSystemValidation();
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
        if (instance instanceof Dependency) {
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
