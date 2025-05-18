/*
 * Copyright 2023 Flamingock (https://oss.flamingock.io)
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

import io.flamingock.core.api.exception.FlamingockException;

public class NotFoundDependencyException extends FlamingockException {

    public NotFoundDependencyException(String dependencyName) {
        super("Dependency/property named '" + dependencyName + "' was not found in the context.");
    }

    public NotFoundDependencyException(Class<?> dependencyType) {
        super("Dependency/property of type '" + dependencyType.getName() + "' was not found in the context.");
    }

    public NotFoundDependencyException(String name, Class<?> dependencyType) {
        super("Dependency/property named '" + name + "' of type '" + dependencyType.getName() + "' was not found in the context.");
    }
}