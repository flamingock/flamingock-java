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
 * CLI-specific implementation of SLF4J Logger that outputs to System.out/System.err.
 * 
 * <p>This logger implementation is designed for command-line interface usage,
 * providing simple, clean output without the overhead of traditional logging frameworks.
 * 
 * <p>Output routing:
 * <ul>
 *   <li>ERROR, WARN → System.err</li>
 *   <li>INFO, DEBUG, TRACE → System.out</li>
 * </ul>
 * 
 * @since 6.0.0
 */
public class CliLoggerImpl implements Logger {
    
    private final String name;
    
    /**
     * Create a new CLI logger with the given name.
     * 
     * @param name the logger name
     */
    public CliLoggerImpl(String name) {
        this.name = name;
    }
    
    // Helper method to format messages
    private String formatMessage(String level, String format, Object... arguments) {
        String message = format;
        if (arguments != null && arguments.length > 0) {
            // Simple {} replacement (not as sophisticated as SLF4J but adequate for CLI)
            for (Object arg : arguments) {
                int index = message.indexOf("{}");
                if (index >= 0) {
                    String argStr = arg != null ? arg.toString() : "null";
                    message = message.substring(0, index) + argStr + message.substring(index + 2);
                }
            }
        }
        return String.format("[%-5s] %s: %s", level, name, message);
    }
    
    private void logToErr(String level, String format, Object... arguments) {
        System.err.println(formatMessage(level, format, arguments));
    }
    
    private void logToOut(String level, String format, Object... arguments) {
        System.out.println(formatMessage(level, format, arguments));
    }
    
    // ERROR level methods
    @Override
    public boolean isErrorEnabled() {
        return CliLoggerFactory.getGlobalLevel().isEnabled(CliLogLevel.ERROR);
    }
    
    @Override
    public void error(String msg) {
        if (isErrorEnabled()) {
            logToErr("ERROR", msg);
        }
    }
    
    @Override
    public void error(String format, Object arg) {
        if (isErrorEnabled()) {
            logToErr("ERROR", format, arg);
        }
    }
    
    @Override
    public void error(String format, Object arg1, Object arg2) {
        if (isErrorEnabled()) {
            logToErr("ERROR", format, arg1, arg2);
        }
    }
    
    @Override
    public void error(String format, Object... arguments) {
        if (isErrorEnabled()) {
            logToErr("ERROR", format, arguments);
        }
    }
    
    @Override
    public void error(String msg, Throwable t) {
        if (isErrorEnabled()) {
            logToErr("ERROR", msg);
            if (t != null) {
                t.printStackTrace(System.err);
            }
        }
    }
    
    // WARN level methods
    @Override
    public boolean isWarnEnabled() {
        return CliLoggerFactory.getGlobalLevel().isEnabled(CliLogLevel.WARN);
    }
    
    @Override
    public void warn(String msg) {
        if (isWarnEnabled()) {
            logToErr("WARN", msg);
        }
    }
    
    @Override
    public void warn(String format, Object arg) {
        if (isWarnEnabled()) {
            logToErr("WARN", format, arg);
        }
    }
    
    @Override
    public void warn(String format, Object arg1, Object arg2) {
        if (isWarnEnabled()) {
            logToErr("WARN", format, arg1, arg2);
        }
    }
    
    @Override
    public void warn(String format, Object... arguments) {
        if (isWarnEnabled()) {
            logToErr("WARN", format, arguments);
        }
    }
    
    @Override
    public void warn(String msg, Throwable t) {
        if (isWarnEnabled()) {
            logToErr("WARN", msg);
            if (t != null) {
                t.printStackTrace(System.err);
            }
        }
    }
    
    // INFO level methods
    @Override
    public boolean isInfoEnabled() {
        return CliLoggerFactory.getGlobalLevel().isEnabled(CliLogLevel.INFO);
    }
    
    @Override
    public void info(String msg) {
        if (isInfoEnabled()) {
            logToOut("INFO", msg);
        }
    }
    
    @Override
    public void info(String format, Object arg) {
        if (isInfoEnabled()) {
            logToOut("INFO", format, arg);
        }
    }
    
    @Override
    public void info(String format, Object arg1, Object arg2) {
        if (isInfoEnabled()) {
            logToOut("INFO", format, arg1, arg2);
        }
    }
    
    @Override
    public void info(String format, Object... arguments) {
        if (isInfoEnabled()) {
            logToOut("INFO", format, arguments);
        }
    }
    
    @Override
    public void info(String msg, Throwable t) {
        if (isInfoEnabled()) {
            logToOut("INFO", msg);
            if (t != null) {
                t.printStackTrace(System.out);
            }
        }
    }
    
    // DEBUG level methods
    @Override
    public boolean isDebugEnabled() {
        return CliLoggerFactory.getGlobalLevel().isEnabled(CliLogLevel.DEBUG);
    }
    
    @Override
    public void debug(String msg) {
        if (isDebugEnabled()) {
            logToOut("DEBUG", msg);
        }
    }
    
    @Override
    public void debug(String format, Object arg) {
        if (isDebugEnabled()) {
            logToOut("DEBUG", format, arg);
        }
    }
    
    @Override
    public void debug(String format, Object arg1, Object arg2) {
        if (isDebugEnabled()) {
            logToOut("DEBUG", format, arg1, arg2);
        }
    }
    
    @Override
    public void debug(String format, Object... arguments) {
        if (isDebugEnabled()) {
            logToOut("DEBUG", format, arguments);
        }
    }
    
    @Override
    public void debug(String msg, Throwable t) {
        if (isDebugEnabled()) {
            logToOut("DEBUG", msg);
            if (t != null) {
                t.printStackTrace(System.out);
            }
        }
    }
    
    // TRACE level methods
    @Override
    public boolean isTraceEnabled() {
        return CliLoggerFactory.getGlobalLevel().isEnabled(CliLogLevel.TRACE);
    }
    
    @Override
    public void trace(String msg) {
        if (isTraceEnabled()) {
            logToOut("TRACE", msg);
        }
    }
    
    @Override
    public void trace(String format, Object arg) {
        if (isTraceEnabled()) {
            logToOut("TRACE", format, arg);
        }
    }
    
    @Override
    public void trace(String format, Object arg1, Object arg2) {
        if (isTraceEnabled()) {
            logToOut("TRACE", format, arg1, arg2);
        }
    }
    
    @Override
    public void trace(String format, Object... arguments) {
        if (isTraceEnabled()) {
            logToOut("TRACE", format, arguments);
        }
    }
    
    @Override
    public void trace(String msg, Throwable t) {
        if (isTraceEnabled()) {
            logToOut("TRACE", msg);
            if (t != null) {
                t.printStackTrace(System.out);
            }
        }
    }
    
    // Marker methods - not used in CLI, but required by interface
    @Override
    public boolean isErrorEnabled(Marker marker) {
        return isErrorEnabled();
    }
    
    @Override
    public void error(Marker marker, String msg) {
        error(msg);
    }
    
    @Override
    public void error(Marker marker, String format, Object arg) {
        error(format, arg);
    }
    
    @Override
    public void error(Marker marker, String format, Object arg1, Object arg2) {
        error(format, arg1, arg2);
    }
    
    @Override
    public void error(Marker marker, String format, Object... arguments) {
        error(format, arguments);
    }
    
    @Override
    public void error(Marker marker, String msg, Throwable t) {
        error(msg, t);
    }
    
    @Override
    public boolean isWarnEnabled(Marker marker) {
        return isWarnEnabled();
    }
    
    @Override
    public void warn(Marker marker, String msg) {
        warn(msg);
    }
    
    @Override
    public void warn(Marker marker, String format, Object arg) {
        warn(format, arg);
    }
    
    @Override
    public void warn(Marker marker, String format, Object arg1, Object arg2) {
        warn(format, arg1, arg2);
    }
    
    @Override
    public void warn(Marker marker, String format, Object... arguments) {
        warn(format, arguments);
    }
    
    @Override
    public void warn(Marker marker, String msg, Throwable t) {
        warn(msg, t);
    }
    
    @Override
    public boolean isInfoEnabled(Marker marker) {
        return isInfoEnabled();
    }
    
    @Override
    public void info(Marker marker, String msg) {
        info(msg);
    }
    
    @Override
    public void info(Marker marker, String format, Object arg) {
        info(format, arg);
    }
    
    @Override
    public void info(Marker marker, String format, Object arg1, Object arg2) {
        info(format, arg1, arg2);
    }
    
    @Override
    public void info(Marker marker, String format, Object... arguments) {
        info(format, arguments);
    }
    
    @Override
    public void info(Marker marker, String msg, Throwable t) {
        info(msg, t);
    }
    
    @Override
    public boolean isDebugEnabled(Marker marker) {
        return isDebugEnabled();
    }
    
    @Override
    public void debug(Marker marker, String msg) {
        debug(msg);
    }
    
    @Override
    public void debug(Marker marker, String format, Object arg) {
        debug(format, arg);
    }
    
    @Override
    public void debug(Marker marker, String format, Object arg1, Object arg2) {
        debug(format, arg1, arg2);
    }
    
    @Override
    public void debug(Marker marker, String format, Object... arguments) {
        debug(format, arguments);
    }
    
    @Override
    public void debug(Marker marker, String msg, Throwable t) {
        debug(msg, t);
    }
    
    @Override
    public boolean isTraceEnabled(Marker marker) {
        return isTraceEnabled();
    }
    
    @Override
    public void trace(Marker marker, String msg) {
        trace(msg);
    }
    
    @Override
    public void trace(Marker marker, String format, Object arg) {
        trace(format, arg);
    }
    
    @Override
    public void trace(Marker marker, String format, Object arg1, Object arg2) {
        trace(format, arg1, arg2);
    }
    
    @Override
    public void trace(Marker marker, String format, Object... arguments) {
        trace(format, arguments);
    }
    
    @Override
    public void trace(Marker marker, String msg, Throwable t) {
        trace(msg, t);
    }
    
    @Override
    public String getName() {
        return name;
    }
}