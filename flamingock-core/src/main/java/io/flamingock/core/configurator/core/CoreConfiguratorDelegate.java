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


import io.flamingock.core.api.SystemModule;
import io.flamingock.core.api.metadata.FlamingockMetadata;
import io.flamingock.core.configurator.SystemModuleManager;
import io.flamingock.core.configurator.TransactionStrategy;
import io.flamingock.core.configurator.legacy.LegacyMigration;
import io.flamingock.core.pipeline.Stage;
import io.flamingock.template.TemplateFactory;
import io.flamingock.template.TemplateModule;

import java.util.Map;
import java.util.function.Supplier;

public class CoreConfiguratorDelegate<
        HOLDER,
        SYSTEM_MODULE extends SystemModule,
        SYSTEM_MODULE_MANAGER extends SystemModuleManager<SYSTEM_MODULE>>
        implements CoreConfigurator<HOLDER, SYSTEM_MODULE, SYSTEM_MODULE_MANAGER> {

    private final Supplier<HOLDER> holderSupplier;
    private final CoreConfiguration configuration;
    private final SYSTEM_MODULE_MANAGER systemModuleManager;
    private FlamingockMetadata flamingockMetadata = FlamingockMetadata.getInstance().orElse(null);

    public CoreConfiguratorDelegate(CoreConfiguration configuration,
                                    Supplier<HOLDER> holderSupplier,
                                    SYSTEM_MODULE_MANAGER systemModuleManager) {
        this.configuration = configuration;
        this.holderSupplier = holderSupplier;
        this.systemModuleManager = systemModuleManager;
    }

    @Override
    public CoreConfigurable getCoreConfiguration() {
        return configuration;
    }

    @Override
    public HOLDER addStage(Stage stage) {
        configuration.addStage(stage);
        return holderSupplier.get();
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
    public HOLDER setTrackIgnored(boolean trackIgnored) {
        configuration.setTrackIgnored(trackIgnored);
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
    public HOLDER setLegacyMigration(LegacyMigration legacyMigration) {
        configuration.setLegacyMigration(legacyMigration);
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
    public HOLDER addTemplateModule(TemplateModule templateModule) {
        TemplateFactory.registerModule(templateModule);
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
    public boolean isTrackIgnored() {
        return configuration.isTrackIgnored();
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
    public LegacyMigration getLegacyMigration() {
        return configuration.getLegacyMigration();
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
    public SYSTEM_MODULE_MANAGER getSystemModuleManager() {
        return systemModuleManager;
    }

    @Override
    public HOLDER setMongockImporterConfiguration(CoreConfiguration.MongockImporterConfiguration mongockImporterConfiguration) {
        configuration.setLegacyMongockChangelogSource(mongockImporterConfiguration.getLegacySourceName());
        return holderSupplier.get();
    }

    @Override
    public CoreConfiguration.MongockImporterConfiguration getMongockImporterConfiguration() {
        return configuration.getMongockImporterConfiguration();
    }

    @Override
    public HOLDER setFlamingockMetadata(FlamingockMetadata metadata) {
        this.flamingockMetadata = metadata;
        return holderSupplier.get();
    }

    @Override
    public FlamingockMetadata getFlamingockMetadata() {
        return flamingockMetadata;
    }

    @Override
    public HOLDER addSystemModule(SYSTEM_MODULE systemModule) {
        getSystemModuleManager().add(systemModule);
        return holderSupplier.get();
    }


}
