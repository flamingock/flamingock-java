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
import io.flamingock.core.configurator.legacy.LegacyMigration;
import io.flamingock.core.pipeline.Stage;

import java.util.List;
import java.util.Map;

public interface CoreConfigurable {

    void setStages(List<Stage> stages);

    void addStage(Stage stage);

    Iterable<Stage> getStages();


    void setLockAcquiredForMillis(long lockAcquiredForMillis);

    void setLockQuitTryingAfterMillis(long lockQuitTryingAfterMillis);

    void setLockTryFrequencyMillis(long lockTryFrequencyMillis);

    void setThrowExceptionIfCannotObtainLock(boolean throwExceptionIfCannotObtainLock);

    void setEnableRefreshDaemon(boolean enableRefreshDaemon);

    boolean isEnableRefreshDaemon();

    void setTrackIgnored(boolean trackIgnored);

    void setEnabled(boolean enabled);

    void setStartSystemVersion(String startSystemVersion);

    void setEndSystemVersion(String endSystemVersion);

    void setServiceIdentifier(String serviceIdentifier);

    void setMetadata(Map<String, Object> metadata);

    void setLegacyMigration(LegacyMigration legacyMigration);

    void setTransactionEnabled(Boolean transactionEnabled);

    void setDefaultAuthor(String defaultAuthor);

    void setTransactionStrategy(TransactionStrategy transactionStrategy);

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

    Boolean getTransactionEnabled();

    String getDefaultAuthor();

    TransactionStrategy getTransactionStrategy();

    void setMongockImporterSource(String mongockImporterSource);

    String getMongockImporterSource();

    default boolean isMongockImporterEnabled() {
        return getMongockImporterSource() != null;
    }

}
