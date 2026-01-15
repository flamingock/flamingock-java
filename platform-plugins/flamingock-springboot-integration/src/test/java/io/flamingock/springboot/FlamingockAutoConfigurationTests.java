/*
 * Copyright 2025 Flamingock (https://www.flamingock.io)
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

import io.flamingock.api.external.targets.TargetSystem;
import io.flamingock.internal.core.external.store.CommunityAuditStore;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;


class FlamingockAutoConfigurationTests {

    private static final String PROFILE = "non-cli";

    private ApplicationContextRunner contextRunner() {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(FlamingockAutoConfiguration.class))
                .withUserConfiguration(TestConfiguration.class)
                .withPropertyValues("spring.profiles.active=" + PROFILE);
    }

    @Test
    void whenModeIsDefault_thenBothBuilderAndRunnerBeansExist() {
        // Default is APPLICATION_RUNNER - both builder and runner beans exist
        contextRunner().run(ctx -> {
            assertThat(ctx).hasBean("flamingock-builder");
            assertThat(ctx).hasBean("flamingock-runner");
        });
    }

    @Test
    void whenModeIsApplicationRunner_thenBothBuilderAndRunnerBeansExist() {
        contextRunner()
                .withPropertyValues("flamingock.management-mode=APPLICATION_RUNNER")
                .run(ctx -> {
                    assertThat(ctx).hasBean("flamingock-builder");
                    assertThat(ctx).hasBean("flamingock-runner");
                });
    }

    @Test
    void whenModeIsInitializingBean_thenBothBeansCreatedAndExecutionAttempted() {
        // InitializingBean executes immediately during bean creation.
        // Both builder and runner beans are created, but execution fails because
        // the test context doesn't have all required Flamingock dependencies.
        contextRunner()
                .withPropertyValues("flamingock.management-mode=INITIALIZING_BEAN")
                .run(ctx -> {
                    // Context fails because InitializingBean tries to run Flamingock
                    assertThat(ctx).hasFailed();
                    // Verify the failure is related to Flamingock execution (FlamingockException)
                    Throwable failure = ctx.getStartupFailure();
                    Throwable rootCause = failure;
                    while (rootCause.getCause() != null) {
                        rootCause = rootCause.getCause();
                    }
                    assertThat(rootCause.getClass().getName()).contains("flamingock");
                });
    }

    @Test
    void whenModeIsDeferred_thenBuilderBeanExistsAndRunnerBeanDoesNot() {
        contextRunner()
                .withPropertyValues("flamingock.management-mode=DEFERRED")
                .run(ctx -> {
                    assertThat(ctx).doesNotHaveBean("flamingock-runner");
                    assertThat(ctx).hasBean("flamingock-builder");
                });
    }

    @Test
    void whenModeIsUnmanaged_thenNoBeansExist() {
        // When UNMANAGED, the entire auto-configuration class should not load
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(FlamingockAutoConfiguration.class))
                .withPropertyValues(
                        "spring.profiles.active=" + PROFILE,
                        "flamingock.management-mode=UNMANAGED"
                )
                .run(ctx -> {
                    assertThat(ctx).doesNotHaveBean(FlamingockAutoConfiguration.class);
                    assertThat(ctx).doesNotHaveBean("flamingock-runner");
                    assertThat(ctx).doesNotHaveBean("flamingock-builder");
                });
    }

    @Configuration
    static class TestConfiguration {
        @Bean
        public List<TargetSystem> targetSystems() {
            return new ArrayList<>();
        }

        @Bean
        public CommunityAuditStore auditStore() {
            return mock(CommunityAuditStore.class);
        }
    }
}
