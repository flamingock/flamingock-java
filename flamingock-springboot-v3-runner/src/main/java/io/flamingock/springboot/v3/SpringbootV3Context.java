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

package io.flamingock.springboot.v3;

import io.flamingock.commons.utils.Constants;
import io.flamingock.core.builder.Flamingock;
import io.flamingock.core.runner.RunnerBuilder;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;

@ConditionalOnExpression("${flamingock.enabled:true}")
public class SpringbootV3Context extends Flamingock {


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
    public RunnerBuilder flamingockBuilder(SpringbootV3Properties configurationProperties,
                                           ApplicationContext springContext,
                                           ApplicationEventPublisher applicationEventPublisher) {
        return builder(
                configurationProperties.getCoreConfiguration(),
                configurationProperties.getCloudProperties(),
                configurationProperties.getLocalConfiguration()
        )
                .addDependency(SpringRunnerType.class, configurationProperties.getRunnerType())
                .addDependency(ApplicationContext.class, springContext)
                .addDependency(ApplicationEventPublisher.class, applicationEventPublisher);
    }


}
