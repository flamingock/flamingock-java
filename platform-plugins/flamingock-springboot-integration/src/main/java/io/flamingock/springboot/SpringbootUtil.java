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

import io.flamingock.internal.core.runner.Runner;
import io.flamingock.internal.util.log.FlamingockLoggerFactory;
import org.slf4j.Logger;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationContext;

public final class SpringbootUtil {
    private static final Logger logger = FlamingockLoggerFactory.getLogger("Springboot");

    private SpringbootUtil() {
    }

    public static InitializingBean toInitializingBean(Runner runner, boolean autoRun) {
        return () -> runIfApply(runner, autoRun);
    }

    public static ApplicationRunner toApplicationRunner(Runner runner, boolean autoRun) {
        return args -> runIfApply(runner, autoRun);
    }

    private static void runIfApply(Runner runner, boolean autoRun) {
        if(autoRun) {
            runner.run();
        }  else {
            logger.info(
                    "Flamingock automatic execution is disabled (flamingock.autorun=false). " +
                            "Changes will not be executed at startup."
            );
        }
    }

    public static String[] getActiveProfiles(ApplicationContext springContext) {
        String[] activeProfiles = springContext.getEnvironment().getActiveProfiles();
        return activeProfiles.length > 0 ? activeProfiles : new String[]{"default"};
    }
}
