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
package io.flamingock.cli.logging;

/**
 * Factory for creating CLI logger instances.
 * 
 * <p>This factory manages the global log level for all CLI loggers
 * and creates logger instances for specific components.
 * 
 * @since 6.0.0
 */
public class CliLoggerFactory {
    
    // Global log level for all CLI loggers
    private static CliLogLevel globalLevel = CliLogLevel.WARN;
    
    private CliLoggerFactory() {
        // Utility class - prevent instantiation
    }
    
    /**
     * Set the global log level for all CLI loggers.
     * 
     * <p>This affects all existing and future logger instances.
     * 
     * @param level the new global log level
     */
    public static void setGlobalLevel(CliLogLevel level) {
        globalLevel = level;
    }
    
    /**
     * Get the current global log level.
     * 
     * @return the current global log level
     */
    public static CliLogLevel getGlobalLevel() {
        return globalLevel;
    }
    
    /**
     * Create a new CLI logger instance with the given name.
     * 
     * @param name the logger name (typically the class or component name)
     * @return a new CliLoggerImpl instance
     */
    public static CliLoggerImpl getLogger(String name) {
        return new CliLoggerImpl(name);
    }
    
    /**
     * Create a new CLI logger instance for the given class.
     * 
     * @param clazz the class to create a logger for
     * @return a new CliLoggerImpl instance
     */
    public static CliLoggerImpl getLogger(Class<?> clazz) {
        return new CliLoggerImpl(clazz.getSimpleName());
    }
}