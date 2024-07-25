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

package io.flamingock.core.configurator.cloud;

public class CloudConfiguration implements CloudConfigurable {

    private String clientSecret;

    private String host;

    private String service;

    private String environment;

    @Override
    public void setHost(String host) {
        this.host = host;
    }

    @Override
    public void setServiceName(String serviceName) {
        this.service = serviceName;
    }

    @Override
    public void setEnvironmentName(String environmentName) {
        this.environment = environmentName;
    }

    @Override
    public void setApiToken(String apiToken) {
        this.clientSecret = apiToken;
    }

    @Override
    public String getApiToken() {
        return clientSecret;
    }

    @Override
    public String getHost() {
        return host;
    }

    @Override
    public String getServiceName() {
        return service;
    }

    @Override
    public String getEnvironmentName() {
        return environment;
    }
}
