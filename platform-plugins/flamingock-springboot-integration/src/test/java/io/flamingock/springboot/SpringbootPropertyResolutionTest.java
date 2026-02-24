/*
 * Copyright 2026 Flamingock (https://www.flamingock.io)
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

import io.flamingock.internal.core.context.PriorityContextResolver;
import io.flamingock.internal.core.context.SimpleContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests proving that {@link PriorityContextResolver} fails to resolve Spring Environment properties
 * through {@link SpringbootDependencyContext}, because it routes property resolution through the
 * dependency path ({@code getDependencyValue} → {@code getDependency}) instead of delegating to
 * the context's {@code getProperty()}/{@code getPropertyAs()}.
 */
class SpringbootPropertyResolutionTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withPropertyValues("flamingock.timeout=5000");

    @Test
    @DisplayName("Should resolve Spring property through PriorityContextResolver")
    void shouldResolveSpringPropertyThroughPriorityContext() {
        contextRunner.run(ctx -> {
            SpringbootDependencyContext springbootContext = new SpringbootDependencyContext(ctx);

            // Sanity: direct call to SpringbootDependencyContext works
            Optional<String> directResult = springbootContext.getProperty("flamingock.timeout");
            assertTrue(directResult.isPresent(), "Direct getProperty on SpringbootDependencyContext should work");
            assertEquals("5000", directResult.get());

            // Bug: when wrapped in PriorityContextResolver, property resolution breaks
            PriorityContextResolver priorityResolver = new PriorityContextResolver(new SimpleContext(), springbootContext);
            Optional<String> result = priorityResolver.getProperty("flamingock.timeout");

            assertTrue(result.isPresent(), "Property 'timeout' should be resolvable through PriorityContextResolver");
            assertEquals("5000", result.get());
        });
    }

    @Test
    @DisplayName("Should resolve Spring bean through PriorityContextResolver (control test)")
    void shouldResolveSpringBeanThroughPriorityContext() {
        contextRunner
                .withUserConfiguration(BeanConfiguration.class)
                .run(ctx -> {
                    SpringbootDependencyContext springbootContext = new SpringbootDependencyContext(ctx);
                    PriorityContextResolver priorityResolver = new PriorityContextResolver(new SimpleContext(), springbootContext);

                    // Bean resolution DOES work through PriorityContextResolver
                    assertTrue(priorityResolver.getDependency(Runnable.class).isPresent(),
                            "Spring bean should be resolvable through PriorityContextResolver");
                });
    }

    @Configuration
    static class BeanConfiguration {
        @Bean
        public Runnable testService() {
            return () -> {};
        }
    }
}
