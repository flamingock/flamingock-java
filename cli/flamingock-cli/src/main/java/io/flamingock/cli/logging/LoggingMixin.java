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

import picocli.CommandLine.Option;

/**
 * Mixin class for CLI logging options.
 * Can be included in any command to provide logging level control.
 * 
 * @since 6.0.0
 */
public class LoggingMixin {
    
    @Option(names = {"--quiet"}, description = "Only show error messages")
    private boolean quiet;
    
    @Option(names = {"--verbose"}, description = "Show informational messages")
    private boolean verbose;
    
    @Option(names = {"--debug"}, description = "Show debug messages")
    private boolean debug;
    
    @Option(names = {"--trace"}, description = "Show trace messages (most verbose)")
    private boolean trace;
    
    /**
     * Initialize logging level based on CLI flags.
     * Should be called before any logging operations.
     */
    public void initializeLogging() {
        CliLogLevel level;
        
        if (quiet) {
            level = CliLogLevel.ERROR;
        } else if (trace) {
            level = CliLogLevel.TRACE;
        } else if (debug) {
            level = CliLogLevel.DEBUG;
        } else if (verbose) {
            level = CliLogLevel.INFO;
        } else {
            // Default: WARN + ERROR
            level = CliLogLevel.WARN;
        }
        
        CliLoggerFactory.setGlobalLevel(level);
    }
}