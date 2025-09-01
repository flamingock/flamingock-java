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
 * Provides global logging level control for all Flamingock operations.
 * 
 * <p>Note: These options must be specified before the command name.
 * Example: {@code flamingock --verbose audit list}
 * 
 * @since 6.0.0
 */
public class LoggingMixin {
    
    @Option(names = {"--quiet"}, 
            description = "Suppress all output except errors. Global option - must be placed before commands.",
            order = 1)
    private boolean quiet;
    
    @Option(names = {"--verbose"}, 
            description = "Enable informational output. Global option - must be placed before commands.",
            order = 2)
    private boolean verbose;
    
    @Option(names = {"--debug"}, 
            description = "Enable debug output for troubleshooting. Global option - must be placed before commands.",
            order = 3)
    private boolean debug;
    
    @Option(names = {"--trace"}, 
            description = "Enable trace output (most detailed). Global option - must be placed before commands.",
            order = 4)
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