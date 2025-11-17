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
package io.flamingock.internal.common.core.util;

import javax.annotation.processing.ProcessingEnvironment;
import javax.tools.Diagnostic;

public class LoggerPreProcessor {

    private final String logPrefix = "[Flamingock] ";

    private final ProcessingEnvironment processingEnv;

    private final boolean verboseEnabled;

    public LoggerPreProcessor(ProcessingEnvironment processingEnv) {
        this.processingEnv = processingEnv;
        this.verboseEnabled = "true".equals(processingEnv.getOptions().get("flamingock.verbose"));
    }

    public void info(String message) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, "\t " + logPrefix + message);
    }

    public void warn(String message) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING, logPrefix + message);
    }

    public void error(String message) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "\t" + logPrefix + message);
    }

    public void verbose(String message) {
        if (verboseEnabled) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, "\t " + logPrefix + message);
        }
    }
}
