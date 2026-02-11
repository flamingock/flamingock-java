/*
 * Copyright 2023 Flamingock (https://www.flamingock.io)
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

public @interface Stage {
    /**
     * Specifies the location where changes are found. This field is mandatory.
     *
     * <p>The location format determines how it's interpreted:
     * <ul>
     *   <li><b>Package name:</b> Contains dots and no slashes (e.g., "com.example.migrations") -
     *       Used to scan for annotated changes in the specified package</li>
     *   <li><b>Resource directory (relative):</b> Starts with "resources/" (e.g., "resources/db/migrations") -
     *       Used to scan for template-based changes in the specified resources directory</li>
     *   <li><b>Resource directory (absolute):</b> Starts with "/" (e.g., "/absolute/path/to/templates") -
     *       Used to scan for template-based changes in the specified absolute path</li>
     * </ul>
     *
     * @return the location where changes are found (mandatory)
     */
    String location();

    /**
     * The name of the stage. If not specified, the name will be automatically derived from the location.
     * 
     * <p>Name derivation rules:
     * <ul>
     *   <li>Package: "com.example.migrations" → "migrations" (last segment)</li>
     *   <li>Resource path: "resources/db/migrations" → "migrations" (last segment)</li>
     *   <li>Absolute path: "/path/to/migrations" → "migrations" (last segment)</li>
     * </ul>
     * 
     * @return the stage name, or empty string for auto-derived name
     */
    String name() default "";
    
    String description() default "";

}