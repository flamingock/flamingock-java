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
package io.flamingock.internal.core.builder;

import io.flamingock.api.targets.TargetSystem;
import io.flamingock.internal.common.core.context.Context;
import io.flamingock.internal.common.core.context.ContextConfigurable;
import io.flamingock.internal.common.core.context.Dependency;
import io.flamingock.internal.core.configuration.core.CoreConfiguration;
import io.flamingock.internal.core.configuration.core.CoreConfigurator;
import io.flamingock.internal.core.context.PriorityContext;
import io.flamingock.internal.core.store.AuditStore;
import io.flamingock.internal.core.store.audit.AuditPersistence;
import io.flamingock.internal.core.store.audit.LifecycleAuditWriter;
import io.flamingock.internal.core.targets.TargetSystemManager;
import io.flamingock.internal.util.log.FlamingockLoggerFactory;
import io.flamingock.internal.util.Property;
import io.flamingock.internal.util.id.RunnerId;
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
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public abstract class AbstractBuilder<AUDIT_STORE extends AuditStore<?>, HOLDER extends AbstractBuilder<AUDIT_STORE, HOLDER>>
        implements
        CoreConfigurator<HOLDER>,
        ContextConfigurable<HOLDER> {
    private static final Logger logger = FlamingockLoggerFactory.getLogger("Builder");

    protected final Context context;
    protected final TargetSystemManager targetSystemManager = new TargetSystemManager();
    protected final CoreConfiguration coreConfiguration;

    protected AUDIT_STORE auditStore;

    ///////////////////////////////////////////////////////////////////////////////////
    //  BUILD

    /// ////////////////////////////////////////////////////////////////////////////////
    protected AbstractBuilder(
            CoreConfiguration coreConfiguration,
            Context context,
            AUDIT_STORE auditStore) {
        this.context = context;
        this.coreConfiguration = coreConfiguration;
        this.auditStore = auditStore;
    }


    protected abstract HOLDER getSelf();

    protected void configureStoreAndTargetSystem(PriorityContext dependencyContext) {
        auditStore.initialize(dependencyContext);
        //remove this, targetSystem should be mandatory
        targetSystemManager.initialize(dependencyContext);
    }

    protected AuditPersistence getAuditPersistence(PriorityContext hierarchicalContext) {
        AuditPersistence persistence = auditStore.getPersistence();
        hierarchicalContext.addDependency(new Dependency(LifecycleAuditWriter.class, persistence));
        return persistence;
    }

    protected RunnerId generateRunnerId() {
        RunnerId runnerId = RunnerId.generate(getServiceIdentifier());
        setProperty(runnerId);
        logger.info("Generated runner id:  {}", runnerId);
        return runnerId;
    }

    protected void validateAuditStore() {
        if (auditStore == null) {
            throw new BuilderException("AuditStore must be configured before running Flamingock. Provide a valid AuditStore via [Builder.setAuditStore(...)]");
        }
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
