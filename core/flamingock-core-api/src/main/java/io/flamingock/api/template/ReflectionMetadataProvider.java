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
package io.flamingock.api.template;

import java.util.Collection;

/**
 * Provides metadata about classes that require reflective access.
 *
 * <p>Implementations of this interface declare a collection of classes that should be registered
 * for reflection at build time—commonly used in native image generation processes such as GraalVM.
 */
public interface ReflectionMetadataProvider {

    /**
     * Returns a collection of classes that should be registered for reflective access.
     *
     * <p>This method does not perform any registration itself—it only declares the classes
     * that need to be registered. The returned collection does not require a specific
     * ordering and may contain any number of class references.
     * </p>
     *
     * @return a collection of classes to be registered for reflection
     */
    Collection<Class<?>> getReflectiveClasses();

}