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

import io.flamingock.api.external.TargetSystem;
import io.flamingock.internal.core.builder.FlamingockFactory;
import io.flamingock.internal.core.builder.AbstractChangeRunnerBuilder;
import io.flamingock.internal.core.external.store.CommunityAuditStore;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Spring Boot auto-configuration for Flamingock.
 *
 * <p>The configuration behavior is controlled by the {@code flamingock.management-mode} property:</p>
 * <ul>
 *   <li>{@code APPLICATION_RUNNER} (default) - Spring creates the builder and executes it as an ApplicationRunner</li>
 *   <li>{@code INITIALIZING_BEAN} - Spring creates the builder and executes it as an InitializingBean</li>
 *   <li>{@code DEFERRED} - Spring creates the builder; the application controls execution</li>
 *   <li>{@code UNMANAGED} - No beans are created; the application manages everything</li>
 * </ul>
 *
 * <p>When {@code flamingock.cli.mode=true}, the runner executes and then calls System.exit()
 * with the appropriate exit code (0 for success, 1 for failure).</p>
 *
 * <p>The builder bean is always created (unless UNMANAGED) and can be overridden by providing
 * your own {@link AbstractChangeRunnerBuilder} bean.</p>
 */
@Configuration
@ConditionalOnClass(name = "org.springframework.boot.SpringApplication")
@ConditionalOnExpression("!'${flamingock.management-mode:APPLICATION_RUNNER}'.toUpperCase().equals('UNMANAGED')")
@EnableConfigurationProperties(SpringbootProperties.class)
public class FlamingockAutoConfiguration {

    /**
     * Creates the Flamingock builder bean.
     * Always created unless management-mode is UNMANAGED or user provides their own builder.
     */
    @Bean("flamingock-builder")
    @ConditionalOnMissingBean(AbstractChangeRunnerBuilder.class)
    public AbstractChangeRunnerBuilder<?, ?> flamingockBuilder(SpringbootProperties configurationProperties,
                                                               ApplicationContext springContext,
                                                               ApplicationEventPublisher applicationEventPublisher,
                                                               @Autowired(required = false) CommunityAuditStore auditStore,
                                                               List<TargetSystem> targetSystems) {
        AbstractChangeRunnerBuilder<?, ?> builder = FlamingockFactory.getEditionAwareBuilder(
                        configurationProperties.getCoreConfiguration(),
                        configurationProperties.getCloudProperties(),
                        configurationProperties.getLocalConfiguration(),
                        auditStore
                )
                .addDependency(SpringbootManagementMode.class, configurationProperties.getManagementMode())
                .addDependency(ApplicationContext.class, springContext)
                .addDependency(ApplicationEventPublisher.class, applicationEventPublisher);

        for (TargetSystem targetSystem : targetSystems) {
            builder.addTargetSystem(targetSystem);
        }

        return builder;
    }

    /**
     * Creates an ApplicationRunner for CLI mode that builds and executes Flamingock,
     * then calls System.exit() with the appropriate exit code.
     * Only created when flamingock.cli.mode=true.
     */
    @Bean("flamingock-runner")
    @ConditionalOnProperty(name = "flamingock.cli.mode", havingValue = "true")
    public ApplicationRunner cliApplicationRunner(AbstractChangeRunnerBuilder<?, ?> builder) {
        return SpringbootUtil.toCliApplicationRunner(builder);
    }

    /**
     * Creates an ApplicationRunner that builds and executes Flamingock at application startup.
     * Only created when management-mode is APPLICATION_RUNNER (the default) and CLI mode is not active.
     */
    @Bean("flamingock-runner")
    @ConditionalOnProperty(name = "flamingock.cli.mode", havingValue = "false", matchIfMissing = true)
    @ConditionalOnExpression("'${flamingock.management-mode:APPLICATION_RUNNER}'.toUpperCase().equals('APPLICATION_RUNNER')")
    public ApplicationRunner applicationRunner(AbstractChangeRunnerBuilder<?, ?> builder) {
        return SpringbootUtil.toApplicationRunner(builder);
    }

    /**
     * Creates an InitializingBean that builds and executes Flamingock during bean initialization.
     * Only created when management-mode is INITIALIZING_BEAN and CLI mode is not active.
     */
    @Bean("flamingock-runner")
    @ConditionalOnProperty(name = "flamingock.cli.mode", havingValue = "false", matchIfMissing = true)
    @ConditionalOnExpression("'${flamingock.management-mode:APPLICATION_RUNNER}'.toUpperCase().equals('INITIALIZING_BEAN')")
    public InitializingBean initializingBeanRunner(AbstractChangeRunnerBuilder<?, ?> builder) {
        return SpringbootUtil.toInitializingBean(builder);
    }
}
