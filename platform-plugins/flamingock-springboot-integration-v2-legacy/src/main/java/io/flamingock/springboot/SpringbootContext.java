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

import io.flamingock.api.targets.TargetSystem;
import io.flamingock.internal.core.builder.change.AbstractChangeRunnerBuilder;
import io.flamingock.internal.core.store.CommunityAuditStore;
import io.flamingock.internal.util.Constants;
import io.flamingock.internal.core.builder.FlamingockFactory;
import io.flamingock.internal.core.runner.RunnerBuilder;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;

import java.util.List;

@ConditionalOnExpression("${flamingock.enabled:true}")
public class SpringbootContext {


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
    public RunnerBuilder flamingockBuilder(SpringbootProperties configurationProperties,
                                           ApplicationContext springContext,
                                           ApplicationEventPublisher applicationEventPublisher,
                                           @Autowired(required = false) CommunityAuditStore auditStore,
                                           List<TargetSystem> targetSystems) {
        AbstractChangeRunnerBuilder<?,?> builder = FlamingockFactory.getEditionAwareBuilder(
                        configurationProperties.getCoreConfiguration(),
                        configurationProperties.getCloudProperties(),
                        configurationProperties.getLocalConfiguration(),
                        auditStore
                )
                .addDependency(SpringRunnerType.class, configurationProperties.getRunnerType())
                .addDependency(ApplicationContext.class, springContext)
                .addDependency(ApplicationEventPublisher.class, applicationEventPublisher);

        for (TargetSystem targetSystem : targetSystems) {
            builder.addTargetSystem(targetSystem);
        }

        return builder;
    }
}
