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
package io.flamingock.springboot;

import io.flamingock.core.processor.util.Deserializer;
import io.flamingock.internal.common.core.preview.PreviewPipeline;
import io.flamingock.internal.core.configuration.cloud.CloudConfigurable;
import io.flamingock.internal.core.configuration.cloud.CloudConfiguration;
import io.flamingock.internal.core.configuration.core.CoreConfigurable;
import io.flamingock.internal.core.configuration.core.CoreConfiguration;
import io.flamingock.internal.core.configuration.community.CommunityConfigurable;
import io.flamingock.internal.core.configuration.community.CommunityConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

@ConfigurationProperties("flamingock")
public class SpringbootProperties
        implements
        CoreConfigurable,
        CommunityConfigurable,
        CloudConfigurable {
    private SpringRunnerType runnerType = SpringRunnerType.ApplicationRunner;

    private final CoreConfiguration coreConfiguration = new CoreConfiguration();

    private final CloudConfiguration cloudConfiguration = new CloudConfiguration();

    private final CommunityConfiguration localConfiguration = new CommunityConfiguration();

    public CoreConfiguration getCoreConfiguration() {
        return coreConfiguration;
    }


    public CommunityConfiguration getLocalConfiguration() {
        return localConfiguration;
    }

    public CloudConfiguration getCloudProperties() {
        return cloudConfiguration;
    }

    @Override
    public PreviewPipeline getPreviewPipeline() {
        return Deserializer.readPreviewPipelineFromFile();
    }

    @Override
    public void setLockAcquiredForMillis(long lockAcquiredForMillis) {
        coreConfiguration.setLockAcquiredForMillis(lockAcquiredForMillis);
    }

    @Override
    public void setLockQuitTryingAfterMillis(long lockQuitTryingAfterMillis) {
        coreConfiguration.setLockQuitTryingAfterMillis(lockQuitTryingAfterMillis);
    }

    @Override
    public void setLockTryFrequencyMillis(long lockTryFrequencyMillis) {
        coreConfiguration.setLockTryFrequencyMillis(lockTryFrequencyMillis);
    }

    @Override
    public void setThrowExceptionIfCannotObtainLock(boolean throwExceptionIfCannotObtainLock) {
        coreConfiguration.setThrowExceptionIfCannotObtainLock(throwExceptionIfCannotObtainLock);
    }

    @Override
    public void setEnableRefreshDaemon(boolean enableRefreshDaemon) {
        coreConfiguration.setEnableRefreshDaemon(enableRefreshDaemon);
    }

    @Override
    public boolean isEnableRefreshDaemon() {
        return coreConfiguration.isEnableRefreshDaemon();
    }


    @Override
    public void setEnabled(boolean enabled) {
        coreConfiguration.setEnabled(enabled);
    }

    @Override
    public void setServiceIdentifier(String serviceIdentifier) {
        coreConfiguration.setServiceIdentifier(serviceIdentifier);
    }

    @Override
    public void setMetadata(Map<String, Object> metadata) {
        coreConfiguration.setMetadata(metadata);
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

    public SpringRunnerType getRunnerType() {
        return runnerType;
    }

    public void setRunnerType(SpringRunnerType runnerType) {
        this.runnerType = runnerType;
    }


    @Override
    public void setHost(String host) {
        cloudConfiguration.setHost(host);
    }

    @Override
    public void setServiceName(String serviceName) {
        cloudConfiguration.setServiceName(serviceName);
    }

    @Override
    public void setEnvironmentName(String environmentName) {
        cloudConfiguration.setEnvironmentName(environmentName);
    }

    @Override
    public void setApiToken(String apiToken) {
        cloudConfiguration.setApiToken(apiToken);
    }

    @Override
    public String getApiToken() {
        return cloudConfiguration.getApiToken();
    }

    @Override
    public String getHost() {
        return cloudConfiguration.getHost();
    }

    @Override
    public String getServiceName() {
        return cloudConfiguration.getServiceName();
    }

    @Override
    public String getEnvironmentName() {
        return cloudConfiguration.getEnvironmentName();
    }

    public boolean isCloudConfigurationEmpty() {
        return cloudConfiguration.getApiToken() == null;
    }
}
