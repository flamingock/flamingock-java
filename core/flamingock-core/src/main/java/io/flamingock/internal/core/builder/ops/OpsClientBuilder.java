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
package io.flamingock.internal.core.builder.ops;

import io.flamingock.internal.common.core.context.Context;
import io.flamingock.internal.common.core.context.Dependency;
import io.flamingock.internal.core.builder.AbstractBuilder;
import io.flamingock.internal.core.configuration.core.CoreConfiguration;
import io.flamingock.internal.core.context.PriorityContext;
import io.flamingock.internal.core.context.SimpleContext;
import io.flamingock.internal.core.store.AuditStore;
import io.flamingock.internal.core.store.CommunityAuditStore;
import io.flamingock.internal.core.store.audit.AuditPersistence;
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

public class OpsClientBuilder
    extends AbstractBuilder<AuditStore<?>, OpsClientBuilder> {
    private static final Logger logger = FlamingockLoggerFactory.getLogger("Builder");

    ///////////////////////////////////////////////////////////////////////////////////
    //  BUILD

    /// ////////////////////////////////////////////////////////////////////////////////

    public OpsClientBuilder(
            CoreConfiguration coreConfiguration,
            Context context,
            AuditStore<?> auditStore) {
        super(coreConfiguration, context, auditStore);
    }

    protected OpsClientBuilder getSelf() {
        return this;
    }

    public OpsClient build() {
        validateAuditStore();
        RunnerId runnerId = generateRunnerId();
        PriorityContext dependencyContext = buildContext();
        configureStoreAndTargetSystem(dependencyContext);
        targetSystemManager.initialize(dependencyContext);
        AuditPersistence persistence = getAuditPersistence(dependencyContext);
        return new OpsClient(runnerId, persistence);
    }

    private PriorityContext buildContext() {
        logger.trace("injecting internal configuration");
        addDependency(coreConfiguration);
        return new PriorityContext(new SimpleContext(), context);
    }



    ///////////////////////////////////////////////////////////////////////////////////
    //  CORE

    /// ////////////////////////////////////////////////////////////////////////////////


    @Override
    public OpsClientBuilder setLockAcquiredForMillis(long lockAcquiredForMillis) {
        coreConfiguration.setLockAcquiredForMillis(lockAcquiredForMillis);
        return getSelf();
    }

    @Override
    public OpsClientBuilder setLockQuitTryingAfterMillis(Long lockQuitTryingAfterMillis) {
        coreConfiguration.setLockQuitTryingAfterMillis(lockQuitTryingAfterMillis);
        return getSelf();
    }

    @Override
    public OpsClientBuilder setLockTryFrequencyMillis(long lockTryFrequencyMillis) {
        coreConfiguration.setLockTryFrequencyMillis(lockTryFrequencyMillis);
        return getSelf();
    }

    @Override
    public OpsClientBuilder setThrowExceptionIfCannotObtainLock(boolean throwExceptionIfCannotObtainLock) {
        coreConfiguration.setThrowExceptionIfCannotObtainLock(throwExceptionIfCannotObtainLock);
        return getSelf();
    }

    @Override
    public OpsClientBuilder setEnabled(boolean enabled) {
        coreConfiguration.setEnabled(enabled);
        return getSelf();
    }

    @Override
    public OpsClientBuilder setServiceIdentifier(String serviceIdentifier) {
        coreConfiguration.setServiceIdentifier(serviceIdentifier);
        return getSelf();
    }

    @Override
    public OpsClientBuilder setMetadata(Map<String, Object> metadata) {
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
    public OpsClientBuilder addDependency(String name, Class<?> type, Object instance) {
        context.addDependency(new Dependency(name, type, instance));
        return getSelf();
    }

    @Override
    public OpsClientBuilder addDependency(Object instance) {
        if (instance instanceof Dependency) {
            context.addDependency(instance);
            return getSelf();
        } else {
            return addDependency(Dependency.DEFAULT_NAME, instance.getClass(), instance);
        }

    }

    @Override
    public OpsClientBuilder addDependency(String name, Object instance) {
        return addDependency(name, instance.getClass(), instance);
    }

    @Override
    public OpsClientBuilder addDependency(Class<?> type, Object instance) {
        return addDependency(Dependency.DEFAULT_NAME, type, instance);
    }

    @Override
    public OpsClientBuilder setProperty(Property property) {
        context.setProperty(property);
        return getSelf();
    }

    @Override
    public OpsClientBuilder setProperty(String key, String value) {
        context.setProperty(key, value);
        return getSelf();
    }

    @Override
    public OpsClientBuilder setProperty(String key, Boolean value) {
        context.setProperty(key, value);
        return getSelf();
    }

    @Override
    public OpsClientBuilder setProperty(String key, Integer value) {
        context.setProperty(key, value);
        return getSelf();
    }

    @Override
    public OpsClientBuilder setProperty(String key, Float value) {
        context.setProperty(key, value);
        return getSelf();
    }

    @Override
    public OpsClientBuilder setProperty(String key, Long value) {
        context.setProperty(key, value);
        return getSelf();
    }

    @Override
    public OpsClientBuilder setProperty(String key, Double value) {
        context.setProperty(key, value);
        return getSelf();
    }

    @Override
    public OpsClientBuilder setProperty(String key, UUID value) {
        context.setProperty(key, value);
        return getSelf();
    }

    @Override
    public OpsClientBuilder setProperty(String key, Currency value) {
        context.setProperty(key, value);
        return getSelf();
    }

    @Override
    public OpsClientBuilder setProperty(String key, Locale value) {
        context.setProperty(key, value);
        return getSelf();
    }

    @Override
    public OpsClientBuilder setProperty(String key, Charset value) {
        context.setProperty(key, value);
        return getSelf();
    }

    @Override
    public OpsClientBuilder setProperty(String key, File value) {
        context.setProperty(key, value);
        return getSelf();
    }

    @Override
    public OpsClientBuilder setProperty(String key, Path value) {
        context.setProperty(key, value);
        return getSelf();
    }

    @Override
    public OpsClientBuilder setProperty(String key, InetAddress value) {
        context.setProperty(key, value);
        return getSelf();
    }

    @Override
    public OpsClientBuilder setProperty(String key, URL value) {
        context.setProperty(key, value);
        return getSelf();
    }

    @Override
    public OpsClientBuilder setProperty(String key, URI value) {
        context.setProperty(key, value);
        return getSelf();
    }

    @Override
    public OpsClientBuilder setProperty(String key, Duration value) {
        context.setProperty(key, value);
        return getSelf();
    }

    @Override
    public OpsClientBuilder setProperty(String key, Period value) {
        context.setProperty(key, value);
        return getSelf();
    }

    @Override
    public OpsClientBuilder setProperty(String key, Instant value) {
        context.setProperty(key, value);
        return getSelf();
    }

    @Override
    public OpsClientBuilder setProperty(String key, LocalDate value) {
        context.setProperty(key, value);
        return getSelf();
    }

    @Override
    public OpsClientBuilder setProperty(String key, LocalTime value) {
        context.setProperty(key, value);
        return getSelf();
    }

    @Override
    public OpsClientBuilder setProperty(String key, LocalDateTime value) {
        context.setProperty(key, value);
        return getSelf();
    }

    @Override
    public OpsClientBuilder setProperty(String key, ZonedDateTime value) {
        context.setProperty(key, value);
        return getSelf();
    }

    @Override
    public OpsClientBuilder setProperty(String key, OffsetDateTime value) {
        context.setProperty(key, value);
        return getSelf();
    }

    @Override
    public OpsClientBuilder setProperty(String key, OffsetTime value) {
        context.setProperty(key, value);
        return getSelf();
    }

    @Override
    public OpsClientBuilder setProperty(String key, java.util.Date value) {
        context.setProperty(key, value);
        return getSelf();
    }

    @Override
    public OpsClientBuilder setProperty(String key, java.sql.Date value) {
        context.setProperty(key, value);
        return getSelf();
    }

    @Override
    public OpsClientBuilder setProperty(String key, Time value) {
        context.setProperty(key, value);
        return getSelf();
    }

    @Override
    public OpsClientBuilder setProperty(String key, Timestamp value) {
        context.setProperty(key, value);
        return getSelf();
    }

    @Override
    public OpsClientBuilder setProperty(String key, String[] value) {
        context.setProperty(key, value);
        return getSelf();
    }

    @Override
    public OpsClientBuilder setProperty(String key, Integer[] value) {
        context.setProperty(key, value);
        return getSelf();
    }

    @Override
    public OpsClientBuilder setProperty(String key, Long[] value) {
        context.setProperty(key, value);
        return getSelf();
    }

    @Override
    public OpsClientBuilder setProperty(String key, Double[] value) {
        context.setProperty(key, value);
        return getSelf();
    }

    @Override
    public OpsClientBuilder setProperty(String key, Float[] value) {
        context.setProperty(key, value);
        return getSelf();
    }

    @Override
    public OpsClientBuilder setProperty(String key, Boolean[] value) {
        context.setProperty(key, value);
        return getSelf();
    }

    @Override
    public OpsClientBuilder setProperty(String key, Byte[] value) {
        context.setProperty(key, value);
        return getSelf();
    }

    @Override
    public OpsClientBuilder setProperty(String key, Short[] value) {
        context.setProperty(key, value);
        return getSelf();
    }

    @Override
    public OpsClientBuilder setProperty(String key, Character[] value) {
        context.setProperty(key, value);
        return getSelf();
    }

    @Override
    public <T extends Enum<T>> OpsClientBuilder setProperty(String key, T value) {
        context.setProperty(key, value);
        return getSelf();
    }

}
