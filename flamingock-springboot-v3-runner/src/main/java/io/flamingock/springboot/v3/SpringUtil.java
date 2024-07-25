/*
 * Copyright 2023 Flamingock (https://oss.flamingock.io)
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

package io.flamingock.springboot.v3;

import org.springframework.context.ApplicationContext;

public final class SpringUtil {

    private SpringUtil() {
    }

    public static String[] getActiveProfiles(ApplicationContext springContext) {
        String[] activeProfiles = springContext.getEnvironment().getActiveProfiles();
        return activeProfiles.length > 0 ? activeProfiles : new String[]{"default"};
    }
}
