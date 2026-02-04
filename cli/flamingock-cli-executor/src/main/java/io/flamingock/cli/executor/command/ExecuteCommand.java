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
package io.flamingock.cli.executor.command;

import picocli.CommandLine;
import picocli.CommandLine.Command;

/**
 * Parent command for execution operations.
 *
 * <p>Groups subcommands related to executing Flamingock changes:</p>
 * <ul>
 *   <li>{@code apply} - Apply pending changes</li>
 * </ul>
 *
 * <p>Future milestones may add additional subcommands such as:</p>
 * <ul>
 *   <li>{@code undo} - Undo last change (M1)</li>
 *   <li>{@code dry-run} - Preview changes without applying (M1)</li>
 *   <li>{@code validate} - Validate change state (M1)</li>
 * </ul>
 */
@Command(
        name = "execute",
        description = "Execute Flamingock operations on an application",
        subcommands = {ApplyCommand.class},
        mixinStandardHelpOptions = true
)
public class ExecuteCommand implements Runnable {

    @CommandLine.ParentCommand
    private Object parent;

    @Override
    public void run() {
        // Show help when no subcommand is specified
        new CommandLine(this).usage(System.out);
    }
}
