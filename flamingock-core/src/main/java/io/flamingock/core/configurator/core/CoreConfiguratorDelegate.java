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

package io.flamingock.core.configurator.core;


import io.flamingock.core.configurator.TransactionStrategy;

import java.util.Map;
import java.util.function.Supplier;

public class CoreConfiguratorDelegate<HOLDER>
        implements CoreConfigurator<HOLDER> {

    private final Supplier<HOLDER> holderSupplier;
    private final CoreConfiguration configuration;

    public CoreConfiguratorDelegate(CoreConfiguration configuration,
                                    Supplier<HOLDER> holderSupplier) {
        this.configuration = configuration;
        this.holderSupplier = holderSupplier;
    }


    @Override
    public CoreConfigurable getCoreConfiguration() {
        return configuration;
    }

    @Override
    public HOLDER setLockAcquiredForMillis(long lockAcquiredForMillis) {
        configuration.setLockAcquiredForMillis(lockAcquiredForMillis);
        return holderSupplier.get();
    }

    @Override
    public HOLDER setLockQuitTryingAfterMillis(Long lockQuitTryingAfterMillis) {
        configuration.setLockQuitTryingAfterMillis(lockQuitTryingAfterMillis);
        return holderSupplier.get();
    }

    @Override
    public HOLDER setLockTryFrequencyMillis(long lockTryFrequencyMillis) {
        configuration.setLockTryFrequencyMillis(lockTryFrequencyMillis);
        return holderSupplier.get();
    }

    @Override
    public HOLDER setThrowExceptionIfCannotObtainLock(boolean throwExceptionIfCannotObtainLock) {
        configuration.setThrowExceptionIfCannotObtainLock(throwExceptionIfCannotObtainLock);
        return holderSupplier.get();
    }

    @Override
    public HOLDER setEnabled(boolean enabled) {
        configuration.setEnabled(enabled);
        return holderSupplier.get();
    }

    @Override
    public HOLDER setStartSystemVersion(String startSystemVersion) {
        configuration.setStartSystemVersion(startSystemVersion);
        return holderSupplier.get();
    }

    @Override
    public HOLDER setEndSystemVersion(String endSystemVersion) {
        configuration.setEndSystemVersion(endSystemVersion);
        return holderSupplier.get();
    }

    @Override
    public HOLDER setServiceIdentifier(String serviceIdentifier) {
        configuration.setServiceIdentifier(serviceIdentifier);
        return holderSupplier.get();
    }

    @Override
    public HOLDER setMetadata(Map<String, Object> metadata) {
        configuration.setMetadata(metadata);
        return holderSupplier.get();
    }

    @Override
    public HOLDER setDefaultAuthor(String publicMigrationAuthor) {
        configuration.setDefaultAuthor(publicMigrationAuthor);
        return holderSupplier.get();
    }

    @Override
    public HOLDER setTransactionStrategy(TransactionStrategy transactionStrategy) {
        configuration.setTransactionStrategy(transactionStrategy);
        return holderSupplier.get();
    }

    @Override
    public long getLockAcquiredForMillis() {
        return configuration.getLockAcquiredForMillis();
    }

    @Override
    public Long getLockQuitTryingAfterMillis() {
        return configuration.getLockQuitTryingAfterMillis();
    }

    @Override
    public long getLockTryFrequencyMillis() {
        return configuration.getLockTryFrequencyMillis();
    }

    @Override
    public boolean isThrowExceptionIfCannotObtainLock() {
        return configuration.isThrowExceptionIfCannotObtainLock();
    }

    @Override
    public boolean isEnabled() {
        return configuration.isEnabled();
    }

    @Override
    public String getStartSystemVersion() {
        return configuration.getStartSystemVersion();
    }

    @Override
    public String getEndSystemVersion() {
        return configuration.getEndSystemVersion();
    }

    @Override
    public String getServiceIdentifier() {
        return configuration.getServiceIdentifier();
    }

    @Override
    public Map<String, Object> getMetadata() {
        return configuration.getMetadata();
    }

    @Override
    public String getDefaultAuthor() {
        return configuration.getDefaultAuthor();
    }

    @Override
    public TransactionStrategy getTransactionStrategy() {
        return configuration.getTransactionStrategy();
    }


    @Override
    public HOLDER withImporter(CoreConfiguration.ImporterConfiguration mongockImporterConfiguration) {
        configuration.setLegacyMongockChangelogSource(mongockImporterConfiguration.getLegacySourceName());
        return holderSupplier.get();
    }

    @Override
    public CoreConfiguration.ImporterConfiguration getMongockImporterConfiguration() {
        return configuration.getMongockImporterConfiguration();
    }


}
