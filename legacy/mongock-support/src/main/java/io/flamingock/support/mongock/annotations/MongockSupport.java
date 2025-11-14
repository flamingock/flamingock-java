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
package io.flamingock.support.mongock.annotations;

import io.flamingock.api.annotations.TargetSystem;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Provides backward compatibility support for existing Mongock-based changes within Flamingock.
 * <p>
 * This annotation allows Flamingock to recognise and execute legacy Mongock changes
 * ({@code @ChangeUnit}) without requiring any modification to those immutable classes.
 * It serves as a bridge layer for systems migrating from Mongock to Flamingock while
 * maintaining operational continuity.
 * </p>
 *
 * <h2>Usage</h2>
 * <p>
 * Annotate a configuration or adapter class with {@code @MongockSupport} to enable
 * Flamingock to discover and register Mongock-originated changes.
 * </p>
 *
 * <h2>Target system linkage</h2>
 * <p>
 * In Flamingock, every change unit must be associated with a target system, typically declared
 * via {@link TargetSystem @TargetSystem(id="...")}. Since legacy Mongock changes cannot be altered,
 * this annotation provides a unified way to assign all imported Mongock changes to a specific
 * target system.
 * </p>
 *
 * <h2>Example</h2>
 * <pre>
 * &#64;MongockSupport(targetSystem = "mongock-target-system")
 * public class MongockChangeAdapter {
 *     // This class enables Flamingock to run Mongock changes
 *     // originally defined with @ChangeUnit annotations.
 * }
 * </pre>
 *
 * <p><b>Note:</b> This annotation should only be used when integrating or migrating
 * from Mongock. For new Flamingock changes, use {@link TargetSystem} directly
 * within the change class definition.</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface MongockSupport {

    /**
     * Defines the identifier of the target system to which all Mongock-originated
     * change units will be bound.
     * <p>
     * This value acts as a logical grouping or context key used by Flamingock
     * to differentiate between multiple systems or databases managed by the same
     * instance.
     * </p>
     *
     * <p><b>Example:</b></p>
     * <pre>
     * &#64;MongockSupport(targetSystem = "mongock-target-system")
     * public class LegacyMongockSupport { }
     * </pre>
     *
     * @return the target system identifier
     */
    String targetSystem();
}
