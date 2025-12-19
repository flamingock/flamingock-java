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
package io.flamingock.springboot.testsupport;

import io.flamingock.internal.core.builder.AbstractChangeRunnerBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

/**
 * Autoconfiguration that provides a {@link FlamingockSpringBootTestSupport} bean for Flamingock testing.
 *
 * <p>This configuration is automatically picked up when using {@link FlamingockSpringBootTest}
 * and provides a ready-to-use {@code FlamingockSpringBootTestSupport} bean that can be autowired
 * directly in tests.</p>
 *
 * <p><strong>Example usage:</strong></p>
 * <pre>
 * &#64;FlamingockSpringBootTest(classes = {MyApp.class, TestConfig.class})
 * class MyFlamingockTest {
 *
 *     &#64;Autowired
 *     private FlamingockSpringBootTestSupport flamingockTestSupport;
 *
 *     &#64;Test
 *     void testMigration() {
 *         flamingockTestSupport.givenBuilderFromContext()
 *             .whenRun()
 *             .thenExpectAuditSequenceStrict(APPLIED(MyChange.class))
 *             .verify();
 *     }
 * }
 * </pre>
 *
 * <p><strong>Note:</strong> The {@code FlamingockSpringBootTestSupport} bean has prototype scope,
 * meaning a new instance is created for each injection point. This is necessary because the
 * underlying {@code GivenStage} accumulates internal state (preconditions, expectations) and
 * is not reusable between tests.</p>
 *
 * @see FlamingockSpringBootTest
 * @see FlamingockSpringBootTestSupport
 * @see io.flamingock.support.FlamingockTestSupport
 * @since 1.0
 */
@Configuration
public class FlamingockTestAutoConfiguration {

    /**
     * Creates a {@link FlamingockSpringBootTestSupport} bean for Flamingock testing.
     *
     * <p>The bean has prototype scope to ensure each test gets its own fresh instance,
     * as the underlying {@code GivenStage} accumulates state and cannot be shared between tests.</p>
     *
     * @param builderFromContext the Flamingock builder, automatically configured by Spring Boot
     * @return a new {@code FlamingockSpringBootTestSupport} instance ready for testing
     */
    @Bean
    @Scope("prototype")
    @ConditionalOnBean(AbstractChangeRunnerBuilder.class)
    public FlamingockSpringBootTestSupport flamingockGivenStage(AbstractChangeRunnerBuilder<?, ?> builderFromContext) {
        return new FlamingockSpringBootTestSupport(builderFromContext);
    }
}
