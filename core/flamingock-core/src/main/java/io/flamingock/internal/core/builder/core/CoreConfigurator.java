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

package io.flamingock.internal.core.builder.core;


import io.flamingock.internal.core.builder.TransactionStrategy;

import java.util.Map;

public interface CoreConfigurator<HOLDER> {

    HOLDER setLockAcquiredForMillis(long lockAcquiredForMillis);

    HOLDER setLockQuitTryingAfterMillis(Long lockQuitTryingAfterMillis);

    HOLDER setLockTryFrequencyMillis(long lockTryFrequencyMillis);

    HOLDER setThrowExceptionIfCannotObtainLock(boolean throwExceptionIfCannotObtainLock);

    HOLDER setEnabled(boolean enabled);

    HOLDER setStartSystemVersion(String startSystemVersion);

    HOLDER setEndSystemVersion(String endSystemVersion);

    HOLDER setServiceIdentifier(String serviceIdentifier);

    HOLDER setMetadata(Map<String, Object> metadata);

    HOLDER setDefaultAuthor(String defaultMigrationAuthor);

    HOLDER setTransactionStrategy(TransactionStrategy transactionStrategy);

    long getLockAcquiredForMillis();

    Long getLockQuitTryingAfterMillis();

    long getLockTryFrequencyMillis();

    boolean isThrowExceptionIfCannotObtainLock();

    boolean isEnabled();

    String getStartSystemVersion();

    String getEndSystemVersion();

    String getServiceIdentifier();

    Map<String, Object> getMetadata();

    String getDefaultAuthor();

    TransactionStrategy getTransactionStrategy();

    HOLDER withImporter(CoreConfiguration.ImporterConfiguration mongockImporterConfiguration);

    CoreConfiguration.ImporterConfiguration getMongockImporterConfiguration();


}
