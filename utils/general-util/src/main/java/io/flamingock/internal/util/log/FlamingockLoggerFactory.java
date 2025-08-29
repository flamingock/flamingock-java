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
package io.flamingock.internal.util.log;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Centralized logger factory for consistent Flamingock logger naming.
 * 
 * <p>All Flamingock loggers use "FK-" prefix with 20-character alignment for clean log output.
 * This factory ensures consistent branding and visual alignment across all components.
 * 
 * <p>Compatible with GraalVM native image compilation - uses only safe string operations
 * and Class.getSimpleName() to avoid reflection issues.
 * 
 * <h3>Usage Examples:</h3>
 * <pre>
 * // String-based logger (preferred for core components)
 * private static final Logger logger = FlamingockLoggerFactory.getLogger("ChangeExecution");
 * 
 * // Class-based logger (useful for community/platform components)  
 * private static final Logger logger = FlamingockLoggerFactory.getLogger(MyClass.class);
 * </pre>
 * 
 * <h3>Output Format:</h3>
 * <pre>
 * FK-ChangeExecution   - Starting change execution [change=create-user-table]
 * FK-Lock              - Process lock acquired successfully [duration=120ms]
 * FK-StageExecutor     - Stage execution completed [duration=1.2s tasks=3]
 * </pre>
 * 
 * @since 6.0.0
 */
public final class FlamingockLoggerFactory {
    
    private static final String PREFIX = "FK-";
    private static final int TOTAL_WIDTH = 20;
    private static final int MAX_NAME_LENGTH = TOTAL_WIDTH - PREFIX.length(); // 17 chars
    
    private FlamingockLoggerFactory() {
        // Utility class - prevent instantiation
    }
    
    /**
     * Create logger with component name.
     * 
     * <p>The name will be prefixed with "FK-" and padded to exactly 20 characters
     * for consistent alignment in log output.
     * 
     * @param name the component name (e.g., "ChangeExecution", "Lock", "StageExecutor")
     * @return SLF4J Logger instance with formatted name
     */
    public static Logger getLogger(String name) {
        String formattedName = formatLoggerName(name);
        return LoggerFactory.getLogger(formattedName);
    }
    
    /**
     * Create logger with class type.
     * 
     * <p>Uses the class simple name (Class.getSimpleName()) which is GraalVM native image safe.
     * The name will be prefixed with "FK-" and padded to exactly 20 characters.
     * 
     * @param clazz the class (simple name will be extracted and used)
     * @return SLF4J Logger instance with formatted name
     */
    public static Logger getLogger(Class<?> clazz) {
        String className = clazz.getSimpleName();
        String formattedName = formatLoggerName(className);
        return LoggerFactory.getLogger(formattedName);
    }
    
    /**
     * Formats a name into the standard Flamingock logger format.
     * 
     * <p>Rules:
     * <ul>
     *   <li>Prefixed with "FK-"</li>
     *   <li>Truncated to 17 characters if longer (to fit within 20-char total)</li>
     *   <li>Right-padded with spaces to exactly 20 characters</li>
     *   <li>Null/empty names default to "Unknown"</li>
     * </ul>
     * 
     * @param name the raw component or class name
     * @return formatted logger name (exactly 20 characters)
     */
    private static String formatLoggerName(String name) {
        if (name == null || name.isEmpty()) {
            name = "Unknown";
        }
        
        // Truncate if too long to fit within total width
        if (name.length() > MAX_NAME_LENGTH) {
            name = name.substring(0, MAX_NAME_LENGTH);
        }
        
        // Format: "FK-ComponentName    " (right-padded to exactly 20 chars)
        return String.format("%-" + TOTAL_WIDTH + "s", PREFIX + name);
    }
}