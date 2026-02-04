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
package io.flamingock.springboot.annotations;

import org.springframework.context.annotation.Profile;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates that a bean should be excluded when the Flamingock CLI runs.
 *
 * <p>This is a convenience meta-annotation that wraps {@code @Profile("!flamingock-cli")}.
 * Use this annotation on components or bean methods that should not be loaded
 * when the application is executed via the Flamingock CLI.</p>
 *
 * <p>Example usage:</p>
 * <pre>
 * &#64;Component
 * &#64;FlamingockCliExcluded
 * public class MyService {
 *     // This bean will not be loaded when running via Flamingock CLI
 * }
 * </pre>
 *
 * <pre>
 * &#64;Configuration
 * public class MyConfig {
 *     &#64;Bean
 *     &#64;FlamingockCliExcluded
 *     public MyBean myBean() {
 *         // This bean will not be loaded when running via Flamingock CLI
 *         return new MyBean();
 *     }
 * }
 * </pre>
 *
 * @see FlamingockCliOnly
 * @see Profile
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Profile("!flamingock-cli")
public @interface FlamingockCliExcluded {
}
