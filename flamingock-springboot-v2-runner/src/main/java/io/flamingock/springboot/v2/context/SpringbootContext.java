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

package io.flamingock.springboot.v2.context;

import io.flamingock.core.engine.local.driver.ConnectionDriver;
import io.flamingock.springboot.v2.SpringRunnerBuilder;
import io.flamingock.springboot.v2.builder.FlamingockSpringboot;
import io.flamingock.springboot.v2.builder.SpringbootBaseBuilder;
import io.flamingock.core.util.Constants;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;

import java.util.Optional;

@ConditionalOnExpression("${flamingock.enabled:true}")
public class SpringbootContext {


    @Bean("flamingock-runner")
    @Profile(Constants.NON_CLI_PROFILE)
    @ConditionalOnExpression("'${flamingock.runner-type:ApplicationRunner}'.toLowerCase().equals('applicationrunner')")
    public ApplicationRunner applicationRunner(SpringRunnerBuilder springbootLocalBuilder) {
        return springbootLocalBuilder.buildApplicationRunner();
    }


    @Bean("flamingock-runner")
    @Profile(Constants.NON_CLI_PROFILE)
    @ConditionalOnExpression("'${flamingock.runner-type:null}'.toLowerCase().equals('initializingbean')")
    public InitializingBean initializingBeanRunner(SpringRunnerBuilder springbootLocalBuilder) {
        return springbootLocalBuilder.buildInitializingBeanRunner();
    }

    @Bean("flamingock-builder")
    @Profile(Constants.NON_CLI_PROFILE)
    public SpringRunnerBuilder localSpringbootBuilder(Optional<ConnectionDriver<?>> connectionDriverOptional,
                                                      FlamingockConfigurationProperties configurationProperties,
                                                      ApplicationContext springContext,
                                                      ApplicationEventPublisher applicationEventPublisher) {
        SpringbootBaseBuilder<?> springRunnerBuilder;
        if (connectionDriverOptional.isPresent() && configurationProperties.isCloudConfigurationEmpty()) {
            springRunnerBuilder = FlamingockSpringboot.localBuilder(
                    configurationProperties.getCoreConfiguration(),
                    configurationProperties.getSpringbootConfiguration(),
                    configurationProperties.getLocalConfiguration()
            ).setDriver(connectionDriverOptional.get());
        } else {
            springRunnerBuilder = FlamingockSpringboot.cloudBuilder(
                    configurationProperties.getCoreConfiguration(),
                    configurationProperties.getSpringbootConfiguration(),
                    configurationProperties.getCloudProperties()
            );
        }
        return springRunnerBuilder
                .setSpringContext(springContext)
                .setEventPublisher(applicationEventPublisher);

    }


}
