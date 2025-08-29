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

import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * SLF4J LoggerFactory implementation for Flamingock CLI.
 * 
 * <p>This factory creates CLI-appropriate loggers that output to System.out/System.err
 * for Flamingock components (those with "FK-" prefix) and creates silent NoOp loggers
 * for all other components to keep CLI output clean.
 * 
 * <p>Logger instances are cached to ensure consistent behavior and avoid duplicate
 * logger creation.
 * 
 * @since 6.0.0
 */
public class CliSLF4JLoggerFactory implements ILoggerFactory {

    private final ConcurrentMap<String, Logger> loggerMap = new ConcurrentHashMap<>();

    @Override
    public Logger getLogger(String name) {
        Logger logger = loggerMap.get(name);
        if (logger == null) {
            Logger newLogger;
            if (name.startsWith("FK-")) {
                // Use CLI logger for Flamingock components
                newLogger = new CliLoggerImpl(name);
            } else {
                // Use NoOp logger for non-Flamingock components to keep output clean
                newLogger = new NoOpLogger(name);
            }
            Logger existingLogger = loggerMap.putIfAbsent(name, newLogger);
            logger = existingLogger == null ? newLogger : existingLogger;
        }
        return logger;
    }
}