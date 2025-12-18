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
package io.flamingock.springboot.support;

import org.springframework.boot.test.context.SpringBootTest;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Meta-annotation for Flamingock Spring Boot integration tests.
 *
 * <p>This annotation combines {@link SpringBootTest} with Flamingock test infrastructure,
 * allowing manual execution control within tests using {@link io.flamingock.support.FlamingockTestSupport}.</p>
 *
 * <p>The test configuration automatically provides an {@link io.flamingock.internal.core.builder.AbstractChangeRunnerBuilder}
 * bean that can be autowired and used with {@code FlamingockTestSupport} for BDD-style testing.</p>
 *
 * <h2>Usage Example</h2>
 * <pre>
 * &#64;FlamingockIntegrationTest
 * public class MyMigrationTest {
 *
 *     &#64;Autowired
 *     private AbstractChangeRunnerBuilder&lt;?, ?&gt; builder;
 *
 *     &#64;Test
 *     &#64;DisplayName("Should apply migration successfully")
 *     void shouldApplyMigration() {
 *         FlamingockTestSupport
 *             .given(builder)
 *             .andExistingAudit(
 *                 APPLIED(SetupChange.class)
 *             )
 *             .whenRun()
 *             .thenExpectAuditSequenceStrict(
 *                 APPLIED(MyMigrationChange.class)
 *             )
 *             .verify();
 *     }
 * }
 * </pre>
 *
 * <h2>Test Infrastructure</h2>
 * <p>Your test configuration should provide a builder bean. Example:</p>
 * <pre>
 * &#64;SpringBootApplication
 * public class TestApplication {
 *     &#64;Bean
 *     public InMemoryTestKit() {
 *         return InMemoryTestKit.create();
 *     }
 *
 *     &#64;Bean
 *     public AbstractChangeRunnerBuilder&lt;?, ?&gt; flamingockBuilder(InMemoryTestKit testKit) {
 *         return testKit.createBuilder().addTargetSystem(myTargetSystem());
 *     }
 * }
 * </pre>
 *
 * @see io.flamingock.support.FlamingockTestSupport
 * @see SpringBootTest
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
public @interface FlamingockIntegrationTest {

    /**
     * Additional properties to be added to the Spring Environment.
     *
     * @return property name-value pairs
     */
    String[] properties() default {};

    /**
     * Application configuration classes to use for loading an ApplicationContext.
     *
     * @return configuration classes
     */
    Class<?>[] classes() default {};
}
