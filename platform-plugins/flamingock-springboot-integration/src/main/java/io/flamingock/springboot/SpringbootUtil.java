/*
 * Copyright 2023 Flamingock (https://www.flamingock.io)
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
package io.flamingock.springboot;

import io.flamingock.internal.core.builder.runner.Runner;
import io.flamingock.internal.core.builder.runner.RunnerBuilder;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationContext;

public final class SpringbootUtil {

    private SpringbootUtil() {
    }

    public static InitializingBean toInitializingBean(RunnerBuilder runnerBuilder) {
        return () -> {
            Runner runner = runnerBuilder.build();
            runner.run();
        };
    }

    public static ApplicationRunner toApplicationRunner(RunnerBuilder runnerBuilder) {
        return args -> {
            Runner runner = runnerBuilder.build();
            runner.run();
        };
    }

    /**
     * Creates an ApplicationRunner for CLI mode that executes Flamingock.
     * If a CliRunner is built (when output file is specified), it handles
     * flush and exit internally. Otherwise, this method handles them.
     *
     * @param runnerBuilder the runner builder
     * @return an ApplicationRunner for CLI execution
     */
    public static ApplicationRunner toCliApplicationRunner(RunnerBuilder runnerBuilder) {
        return args -> {
            Runner runner = runnerBuilder.build();
            runner.run();
        };
    }

    public static String[] getActiveProfiles(ApplicationContext springContext) {
        String[] activeProfiles = springContext.getEnvironment().getActiveProfiles();
        return activeProfiles.length > 0 ? activeProfiles : new String[]{"default"};
    }
}
