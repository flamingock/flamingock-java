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
package io.flamingock.api.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks the method that applies a template-based change to the target system.
 * This method contains the forward change logic that evolves your system state.
 *
 * <p>The method can accept dependency-injected parameters from the Flamingock context,
 * including database connections, repositories, and custom dependencies.
 *
 * <p>This annotation is specifically for use in {@link ChangeTemplate} classes.
 * For code-based changes, use {@code @Apply} instead.
 *
 * @see ChangeTemplate
 * @see RollbackTemplate
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ApplyTemplate {

}
