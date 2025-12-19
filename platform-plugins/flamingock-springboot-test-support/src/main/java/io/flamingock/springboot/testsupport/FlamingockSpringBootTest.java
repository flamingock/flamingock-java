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

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for Flamingock integration tests with Spring Boot.
 *
 * <p>This annotation configures a Spring Boot test context with Flamingock's
 * management mode set to DEFERRED ({@code flamingock.management-mode=DEFERRED}),
 * allowing tests to manually control when Flamingock executes.</p>
 *
 * <p><strong>Usage:</strong></p>
 * <pre>
 * &#64;ExtendWith(SpringExtension.class)  // Required for Spring Boot &lt; 2.1
 * &#64;FlamingockSpringBootTest(classes = {MyApplication.class, TestConfig.class})
 * class MyFlamingockTest {
 *
 *     &#64;Autowired
 *     private AbstractChangeRunnerBuilder&lt;?, ?&gt; flamingockBuilder;
 *
 *     &#64;Test
 *     void testMigration() {
 *         // Setup preconditions...
 *
 *         // Execute Flamingock manually
 *         flamingockBuilder.build().run();
 *
 *         // Verify results...
 *     }
 * }
 * </pre>
 *
 * <p><strong>Note:</strong> For Spring Boot 2.0.x, you must add
 * {@code @ExtendWith(SpringExtension.class)} to your test class.
 * Spring Boot 2.1+ does not require this.</p>
 *
 * @see SpringBootTest
 * @since 1.0
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@SpringBootTest(properties = "flamingock.management-mode=DEFERRED")
public @interface FlamingockSpringBootTest {

    /**
     * Alias for {@link SpringBootTest#classes()}.
     * The component classes to use for loading an ApplicationContext.
     *
     * @return the component classes
     * @see SpringBootTest#classes()
     */
    @AliasFor(annotation = SpringBootTest.class, attribute = "classes")
    Class<?>[] classes() default {};

    /**
     * Alias for {@link SpringBootTest#webEnvironment()}.
     * The type of web environment to create when applicable.
     *
     * @return the web environment mode
     * @see SpringBootTest#webEnvironment()
     */
    @AliasFor(annotation = SpringBootTest.class, attribute = "webEnvironment")
    SpringBootTest.WebEnvironment webEnvironment() default SpringBootTest.WebEnvironment.MOCK;
}
