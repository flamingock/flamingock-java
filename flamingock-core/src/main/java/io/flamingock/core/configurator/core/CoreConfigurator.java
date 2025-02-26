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
import io.flamingock.template.TemplateModule;

import java.util.Map;

public interface CoreConfigurator<
        HOLDER,
        SYSTEM_MODULE extends SystemModule,
        SYSTEM_MODULE_MANAGER extends SystemModuleManager<SYSTEM_MODULE>> {

    CoreConfigurable getCoreConfiguration();

    HOLDER addStage(Stage stage);

    HOLDER setLockAcquiredForMillis(long lockAcquiredForMillis);

    HOLDER setLockQuitTryingAfterMillis(Long lockQuitTryingAfterMillis);

    HOLDER setLockTryFrequencyMillis(long lockTryFrequencyMillis);

    HOLDER setThrowExceptionIfCannotObtainLock(boolean throwExceptionIfCannotObtainLock);

    HOLDER setTrackIgnored(boolean trackIgnored);

    HOLDER setEnabled(boolean enabled);

    HOLDER setStartSystemVersion(String startSystemVersion);

    HOLDER setEndSystemVersion(String endSystemVersion);

    HOLDER setServiceIdentifier(String serviceIdentifier);

    HOLDER setMetadata(Map<String, Object> metadata);

    HOLDER setLegacyMigration(LegacyMigration legacyMigration);

    HOLDER setDefaultAuthor(String defaultMigrationAuthor);

    HOLDER setTransactionStrategy(TransactionStrategy transactionStrategy);

    HOLDER addTemplateModule(TemplateModule templateModule);


    long getLockAcquiredForMillis();

    Long getLockQuitTryingAfterMillis();

    long getLockTryFrequencyMillis();

    boolean isThrowExceptionIfCannotObtainLock();

    boolean isTrackIgnored();

    boolean isEnabled();

    String getStartSystemVersion();

    String getEndSystemVersion();

    String getServiceIdentifier();

    Map<String, Object> getMetadata();

    LegacyMigration getLegacyMigration();

    String getDefaultAuthor();

    TransactionStrategy getTransactionStrategy();


    HOLDER addSystemModule(SYSTEM_MODULE systemModule);

    SYSTEM_MODULE_MANAGER getSystemModuleManager();

    HOLDER withImporter(CoreConfiguration.ImporterConfiguration mongockImporterConfiguration);

    CoreConfiguration.ImporterConfiguration getMongockImporterConfiguration();

    HOLDER setFlamingockMetadata(FlamingockMetadata metadata);

    FlamingockMetadata getFlamingockMetadata();

}
