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
package io.flamingock.internal.core.context;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests proving that {@link PriorityContextResolver} fails to delegate property resolution
 * to the base context when that context keeps dependencies and properties in separate stacks.
 * <p>
 * The bug: {@code getPropertyAs()} routes through {@code getDependencyValue(name, type)} which calls
 * {@code getDependency(name)}, never reaching the base context's {@code getProperty()}/{@code getPropertyAs()}.
 * This works for {@link SimpleContext} (which stores properties as dependencies) but breaks for any
 * {@code ContextResolver} that separates the two (like {@code SpringbootDependencyContext}).
 */
class PriorityContextResolverPropertyTest {

    @Test
    @DisplayName("Should resolve property from base context when property is in a separate stack (not a dependency)")
    void shouldResolvePropertyFromBaseContext_whenPropertyInSeparateStack() {
        // Given: a base context that stores properties separately from dependencies
        SplitStackContextResolver splitStackContext = new SplitStackContextResolver();
        splitStackContext.addProperty("timeout", "5000");

        // When: wrapped in PriorityContextResolver as the base context
        PriorityContextResolver priorityResolver = new PriorityContextResolver(new SimpleContext(), splitStackContext);

        // Then: the property should be resolvable
        Optional<String> result = priorityResolver.getProperty("timeout");
        assertTrue(result.isPresent(), "Property 'timeout' should be present but was empty");
        assertEquals("5000", result.get());
    }

    @Test
    @DisplayName("Should resolve both dependency and property from base context when both exist in separate stacks")
    void shouldResolvePropertyFromBaseContext_whenDependencyAlsoExists() {
        // Given: a base context with both a dependency (bean) and a property in separate stacks
        SplitStackContextResolver splitStackContext = new SplitStackContextResolver();
        splitStackContext.addDependency("myService", Runnable.class, (Runnable) () -> {});
        splitStackContext.addProperty("timeout", "5000");

        PriorityContextResolver priorityResolver = new PriorityContextResolver(new SimpleContext(), splitStackContext);

        // Sanity: the dependency IS resolvable through the priority context
        assertTrue(priorityResolver.getDependency(Runnable.class).isPresent(),
                "Dependency should be resolvable through PriorityContextResolver");

        // Then: the property should also be resolvable
        Optional<String> result = priorityResolver.getProperty("timeout");
        assertTrue(result.isPresent(), "Property 'timeout' should be present but was empty");
        assertEquals("5000", result.get());
    }

    @Test
    @DisplayName("Should resolve property from priority context when set via SimpleContext.setProperty (control test)")
    void shouldResolvePropertyFromPriorityContext_whenPropertySetAsProperty() {
        // Given: a SimpleContext (priority) with a property set directly
        // In SimpleContext, setProperty stores it as a dependency, so getDependencyValue works
        SimpleContext simpleContext = new SimpleContext();
        simpleContext.setProperty("timeout", "5000");

        PriorityContextResolver priorityResolver = new PriorityContextResolver(simpleContext, new SimpleContext());

        // Then: this DOES work because SimpleContext stores properties as dependencies
        Optional<String> result = priorityResolver.getProperty("timeout");
        assertTrue(result.isPresent(), "Property 'timeout' should be present");
        assertEquals("5000", result.get());
    }
}
