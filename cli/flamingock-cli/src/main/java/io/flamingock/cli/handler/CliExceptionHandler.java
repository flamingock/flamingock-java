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
package io.flamingock.cli.handler;

import picocli.CommandLine;
import picocli.CommandLine.IExecutionExceptionHandler;
import picocli.CommandLine.ParseResult;
import picocli.CommandLine.UnmatchedArgumentException;

import java.io.PrintWriter;
import java.util.Arrays;
import java.util.List;

/**
 * CLI exception handler that provides helpful guidance for common CLI errors.
 * 
 * <p>This handler improves user experience by:
 * <ul>
 *   <li>Providing clear error messages</li>
 *   <li>Suggesting corrections for common mistakes</li>
 *   <li>Guiding users to proper usage patterns</li>
 * </ul>
 * 
 * @since 6.0.0
 */
public class CliExceptionHandler implements IExecutionExceptionHandler {
    
    private static final List<String> GLOBAL_FLAGS = Arrays.asList(
        "--verbose", "--debug", "--trace", "--quiet"
    );
    
    private static final List<String> COMMANDS = Arrays.asList(
        "audit", "issue"
    );
    
    @Override
    public int handleExecutionException(Exception ex, CommandLine commandLine, ParseResult parseResult) {
        PrintWriter err = commandLine.getErr();
        
        if (ex instanceof UnmatchedArgumentException) {
            return handleUnmatchedArgument((UnmatchedArgumentException) ex, commandLine, err);
        }
        
        // Professional error format
        err.println();
        err.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        err.println("⚠ Error: " + ex.getMessage());
        err.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        
        // Only show stack trace in debug mode
        if (System.getProperty("flamingock.debug") != null || 
            System.getenv("FLAMINGOCK_DEBUG") != null) {
            err.println();
            err.println("Debug information:");
            ex.printStackTrace(err);
        } else {
            err.println();
            err.println("For detailed error information, run with --debug flag");
        }
        
        return 1;
    }
    
    private int handleUnmatchedArgument(UnmatchedArgumentException ex, CommandLine commandLine, PrintWriter err) {
        String unmatchedArg = ex.getUnmatched().isEmpty() ? "" : ex.getUnmatched().get(0);
        
        // Check if user tried to use a global flag after a command
        if (isGlobalFlag(unmatchedArg)) {
            String currentCommand = getCurrentCommand(parseResult(commandLine));
            
            err.println();
            err.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            err.println("⚠ Error: Unknown option '" + unmatchedArg + "'");
            err.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            err.println();
            err.println("💡 Hint: Global options must be placed BEFORE commands.");
            err.println();
            err.println("✗ Incorrect: flamingock " + currentCommand + " " + unmatchedArg);
            err.println("✓ Correct:   flamingock " + unmatchedArg + " " + currentCommand);
            err.println();
            err.println("Examples:");
            err.println("  flamingock --verbose audit list");
            err.println("  flamingock --debug -c config.yml issue list");
            err.println();
            err.println("For help: flamingock --help");
            
            return 1;
        }
        
        // Check for common typos
        String suggestion = findSuggestion(unmatchedArg);
        
        err.println();
        err.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        err.println("⚠ Error: " + ex.getMessage());
        err.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        
        if (suggestion != null) {
            err.println();
            err.println("💡 Did you mean: " + suggestion + "?");
        }
        
        err.println();
        err.println("For available options, use: flamingock --help");
        
        return 1;
    }
    
    private boolean isGlobalFlag(String arg) {
        return GLOBAL_FLAGS.stream().anyMatch(arg::equals);
    }
    
    private String getCurrentCommand(ParseResult parseResult) {
        if (parseResult == null) return "";
        
        StringBuilder cmd = new StringBuilder();
        ParseResult current = parseResult;
        
        while (current != null) {
            if (current.commandSpec().name() != null && !current.commandSpec().name().equals("flamingock")) {
                if (cmd.length() > 0) cmd.append(" ");
                cmd.append(current.commandSpec().name());
            }
            current = current.hasSubcommand() ? current.subcommand() : null;
        }
        
        return cmd.toString();
    }
    
    private ParseResult parseResult(CommandLine commandLine) {
        try {
            // Use reflection to get the parse result if available
            java.lang.reflect.Field field = CommandLine.class.getDeclaredField("parseResult");
            field.setAccessible(true);
            return (ParseResult) field.get(commandLine);
        } catch (Exception e) {
            return null;
        }
    }
    
    private String findSuggestion(String input) {
        // Common typos and suggestions
        if (input.equals("-verbose") || input.equals("verbose")) return "--verbose";
        if (input.equals("-debug") || input.equals("debug")) return "--debug";
        if (input.equals("-quiet") || input.equals("quiet")) return "--quiet";
        if (input.equals("-trace") || input.equals("trace")) return "--trace";
        if (input.equals("--v")) return "--verbose";
        if (input.equals("--d")) return "--debug";
        if (input.equals("--q")) return "--quiet";
        if (input.equals("--t")) return "--trace";
        
        // Command typos
        if (input.equals("audits")) return "audit";
        if (input.equals("issues")) return "issue";
        if (input.equals("ls")) return "list";
        
        return null;
    }
}