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
 * Log levels for CLI output.
 * 
 * <p>Each level has a priority value. A message is logged if its level's
 * priority is less than or equal to the configured global log level.
 * 
 * @since 6.0.0
 */
public enum CliLogLevel {
    ERROR(1),
    WARN(2),
    INFO(3),
    DEBUG(4),
    TRACE(5);
    
    private final int priority;
    
    CliLogLevel(int priority) {
        this.priority = priority;
    }
    
    /**
     * Check if this level would enable the given level.
     * 
     * <p>For example, if this level is INFO, it enables ERROR, WARN, and INFO,
     * but not DEBUG or TRACE.
     * 
     * @param other the level to check
     * @return true if messages at the other level should be logged
     */
    public boolean isEnabled(CliLogLevel other) {
        return other.priority <= this.priority;
    }
    
    /**
     * Get the priority value.
     * 
     * @return priority (lower values are more important)
     */
    public int getPriority() {
        return priority;
    }
}