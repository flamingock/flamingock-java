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

import org.slf4j.Logger;
import org.slf4j.Marker;

/**
 * No-operation logger implementation for non-Flamingock components.
 * 
 * <p>This logger silently discards all log messages to keep CLI output clean.
 * It's used for third-party libraries and other components that shouldn't
 * produce output in a CLI environment.
 * 
 * <p>All logging methods are no-ops, and all isXxxEnabled() methods return false.
 * 
 * @since 6.0.0
 */
public class NoOpLogger implements Logger {

    private final String name;

    /**
     * Create a new NoOp logger with the given name.
     * 
     * @param name the logger name
     */
    public NoOpLogger(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    // All enabled checks return false
    @Override
    public boolean isTraceEnabled() {
        return false;
    }

    @Override
    public boolean isTraceEnabled(Marker marker) {
        return false;
    }

    @Override
    public boolean isDebugEnabled() {
        return false;
    }

    @Override
    public boolean isDebugEnabled(Marker marker) {
        return false;
    }

    @Override
    public boolean isInfoEnabled() {
        return false;
    }

    @Override
    public boolean isInfoEnabled(Marker marker) {
        return false;
    }

    @Override
    public boolean isWarnEnabled() {
        return false;
    }

    @Override
    public boolean isWarnEnabled(Marker marker) {
        return false;
    }

    @Override
    public boolean isErrorEnabled() {
        return false;
    }

    @Override
    public boolean isErrorEnabled(Marker marker) {
        return false;
    }

    // All trace methods are no-ops
    @Override
    public void trace(String msg) {
        // no-op
    }

    @Override
    public void trace(String format, Object arg) {
        // no-op
    }

    @Override
    public void trace(String format, Object arg1, Object arg2) {
        // no-op
    }

    @Override
    public void trace(String format, Object... arguments) {
        // no-op
    }

    @Override
    public void trace(String msg, Throwable t) {
        // no-op
    }

    @Override
    public void trace(Marker marker, String msg) {
        // no-op
    }

    @Override
    public void trace(Marker marker, String format, Object arg) {
        // no-op
    }

    @Override
    public void trace(Marker marker, String format, Object arg1, Object arg2) {
        // no-op
    }

    @Override
    public void trace(Marker marker, String format, Object... argArray) {
        // no-op
    }

    @Override
    public void trace(Marker marker, String msg, Throwable t) {
        // no-op
    }

    // All debug methods are no-ops
    @Override
    public void debug(String msg) {
        // no-op
    }

    @Override
    public void debug(String format, Object arg) {
        // no-op
    }

    @Override
    public void debug(String format, Object arg1, Object arg2) {
        // no-op
    }

    @Override
    public void debug(String format, Object... arguments) {
        // no-op
    }

    @Override
    public void debug(String msg, Throwable t) {
        // no-op
    }

    @Override
    public void debug(Marker marker, String msg) {
        // no-op
    }

    @Override
    public void debug(Marker marker, String format, Object arg) {
        // no-op
    }

    @Override
    public void debug(Marker marker, String format, Object arg1, Object arg2) {
        // no-op
    }

    @Override
    public void debug(Marker marker, String format, Object... arguments) {
        // no-op
    }

    @Override
    public void debug(Marker marker, String msg, Throwable t) {
        // no-op
    }

    // All info methods are no-ops
    @Override
    public void info(String msg) {
        // no-op
    }

    @Override
    public void info(String format, Object arg) {
        // no-op
    }

    @Override
    public void info(String format, Object arg1, Object arg2) {
        // no-op
    }

    @Override
    public void info(String format, Object... arguments) {
        // no-op
    }

    @Override
    public void info(String msg, Throwable t) {
        // no-op
    }

    @Override
    public void info(Marker marker, String msg) {
        // no-op
    }

    @Override
    public void info(Marker marker, String format, Object arg) {
        // no-op
    }

    @Override
    public void info(Marker marker, String format, Object arg1, Object arg2) {
        // no-op
    }

    @Override
    public void info(Marker marker, String format, Object... arguments) {
        // no-op
    }

    @Override
    public void info(Marker marker, String msg, Throwable t) {
        // no-op
    }

    // All warn methods are no-ops
    @Override
    public void warn(String msg) {
        // no-op
    }

    @Override
    public void warn(String format, Object arg) {
        // no-op
    }

    @Override
    public void warn(String format, Object... arguments) {
        // no-op
    }

    @Override
    public void warn(String format, Object arg1, Object arg2) {
        // no-op
    }

    @Override
    public void warn(String msg, Throwable t) {
        // no-op
    }

    @Override
    public void warn(Marker marker, String msg) {
        // no-op
    }

    @Override
    public void warn(Marker marker, String format, Object arg) {
        // no-op
    }

    @Override
    public void warn(Marker marker, String format, Object arg1, Object arg2) {
        // no-op
    }

    @Override
    public void warn(Marker marker, String format, Object... arguments) {
        // no-op
    }

    @Override
    public void warn(Marker marker, String msg, Throwable t) {
        // no-op
    }

    // All error methods are no-ops
    @Override
    public void error(String msg) {
        // no-op
    }

    @Override
    public void error(String format, Object arg) {
        // no-op
    }

    @Override
    public void error(String format, Object arg1, Object arg2) {
        // no-op
    }

    @Override
    public void error(String format, Object... arguments) {
        // no-op
    }

    @Override
    public void error(String msg, Throwable t) {
        // no-op
    }

    @Override
    public void error(Marker marker, String msg) {
        // no-op
    }

    @Override
    public void error(Marker marker, String format, Object arg) {
        // no-op
    }

    @Override
    public void error(Marker marker, String format, Object arg1, Object arg2) {
        // no-op
    }

    @Override
    public void error(Marker marker, String format, Object... arguments) {
        // no-op
    }

    @Override
    public void error(Marker marker, String msg, Throwable t) {
        // no-op
    }
}