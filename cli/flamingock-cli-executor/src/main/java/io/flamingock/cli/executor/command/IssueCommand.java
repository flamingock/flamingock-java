/*
 * Copyright 2026 Flamingock (https://www.flamingock.io)
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
 * Parent command for issue operations.
 *
 * <p>Groups subcommands related to audit issues:</p>
 * <ul>
 *   <li>{@code list} - List audit issues</li>
 *   <li>{@code get} - Get details for a specific issue</li>
 * </ul>
 */
@Command(
        name = "issue",
        description = "Issue operations for inspecting audit problems",
        subcommands = {ListIssueCommand.class, GetIssueCommand.class},
        mixinStandardHelpOptions = true
)
public class IssueCommand implements Runnable {

    @CommandLine.ParentCommand
    private Object parent;

    @Override
    public void run() {
        new CommandLine(this).usage(System.out);
    }
}
