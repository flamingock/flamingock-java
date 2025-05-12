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

package io.flamingock.springboot.v2;

import io.flamingock.commons.utils.Constants;
import io.flamingock.core.builder.cloud.CloudConfiguration;
import io.flamingock.core.builder.cloud.CloudSystemModuleManager;
import io.flamingock.core.builder.core.CoreConfiguration;
import io.flamingock.core.builder.local.CommunityConfiguration;
import io.flamingock.core.builder.local.LocalSystemModuleManager;
import io.flamingock.core.builder.CloudFlamingockBuilder;
import io.flamingock.core.builder.CommunityFlamingockBuilder;
import io.flamingock.core.community.driver.LocalDriver;
import io.flamingock.core.runner.RunnerBuilder;
import io.flamingock.core.runtime.dependency.DependencyInjectableContext;
import io.flamingock.core.runtime.dependency.SimpleDependencyInjectableContext;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;

import java.util.Optional;

@ConditionalOnExpression("${flamingock.enabled:true}")
public class SpringbootV2Context {


    @Bean("flamingock-runner")
    @Profile(Constants.NON_CLI_PROFILE)
    @ConditionalOnExpression("'${flamingock.runner-type:ApplicationRunner}'.toLowerCase().equals('applicationrunner')")
    public ApplicationRunner applicationRunner(RunnerBuilder runnerBuilder) {
        return SpringbootUtil.toApplicationRunner(runnerBuilder.build());
    }


    @Bean("flamingock-runner")
    @Profile(Constants.NON_CLI_PROFILE)
    @ConditionalOnExpression("'${flamingock.runner-type:null}'.toLowerCase().equals('initializingbean')")
    public InitializingBean initializingBeanRunner(RunnerBuilder runnerBuilder) {
        return SpringbootUtil.toInitializingBean(runnerBuilder.build());
    }

    @Bean("flamingock-builder")
    @Profile(Constants.NON_CLI_PROFILE)
    public RunnerBuilder flamingockBuilder(Optional<LocalDriver> connectionDriverOptional,
                                           SpringbootV2Properties configurationProperties,
                                           ApplicationContext springContext,
                                           ApplicationEventPublisher applicationEventPublisher) {

        if (connectionDriverOptional.isPresent() && configurationProperties.isCloudConfigurationEmpty()) {
            return new SpringbootV2LocalBuilder(
                            configurationProperties.getCoreConfiguration(),
                            configurationProperties.getLocalConfiguration(),
                            new SimpleDependencyInjectableContext(),//
                            new LocalSystemModuleManager()
                    )
                    .addDependency(SpringRunnerType.class, configurationProperties.getRunnerType())
                    .addDependency(ApplicationContext.class, springContext)
                    .addDependency(ApplicationEventPublisher.class, applicationEventPublisher);

        } else {
            return new SpringbootV2CloudBuilder(
                            configurationProperties.getCoreConfiguration(),
                            configurationProperties.getCloudProperties(),
                            new SimpleDependencyInjectableContext(),
                            new CloudSystemModuleManager()
                    )
                    .addDependency(SpringRunnerType.class, configurationProperties.getRunnerType())
                    .addDependency(ApplicationContext.class, springContext)
                    .addDependency(ApplicationEventPublisher.class, applicationEventPublisher);
        }

    }

    /**
     * This class is just to have access to the protected constructor
     */
    private static class SpringbootV2LocalBuilder extends CommunityFlamingockBuilder {
        protected SpringbootV2LocalBuilder(CoreConfiguration coreConfiguration,
                                           CommunityConfiguration communityConfiguration,
                                           DependencyInjectableContext dependencyInjectableContext,
                                           LocalSystemModuleManager systemModuleManager) {
            super(coreConfiguration, communityConfiguration, dependencyInjectableContext, systemModuleManager);
        }
    }

    /**
     * This class is just to have access to the protected constructor
     */
    private static class SpringbootV2CloudBuilder extends CloudFlamingockBuilder {

        protected SpringbootV2CloudBuilder(CoreConfiguration coreConfiguration, CloudConfiguration cloudConfiguration, DependencyInjectableContext dependencyInjectableContext, CloudSystemModuleManager systemModuleManager) {
            super(coreConfiguration, cloudConfiguration, dependencyInjectableContext, systemModuleManager);
        }
    }

}
