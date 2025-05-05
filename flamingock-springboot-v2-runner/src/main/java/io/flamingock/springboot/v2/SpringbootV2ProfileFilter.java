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

package io.flamingock.springboot.v2;

import io.flamingock.core.task.filter.TaskFilter;
import io.flamingock.core.task.loaded.AbstractLoadedTask;
import io.flamingock.core.task.loaded.AbstractReflectionLoadedTask;
import io.flamingock.core.task.loaded.CodeLoadedChangeUnit;
import io.flamingock.core.task.loaded.TemplateLoadedChangeUnit;
import org.springframework.context.annotation.Profile;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class SpringbootV2ProfileFilter implements TaskFilter {

    private final List<String> activeProfiles;

    public SpringbootV2ProfileFilter(String... activeProfiles) {
        this.activeProfiles = activeProfiles.length > 0 ? Arrays.asList(activeProfiles) : Collections.emptyList();
    }

    private static boolean isNegativeProfile(String profile) {
        return profile.charAt(0) == '!';
    }

    @Override
    public boolean filter(AbstractLoadedTask descriptor) {
        if (AbstractReflectionLoadedTask.class.isAssignableFrom(descriptor.getClass())) {
            return filter((AbstractReflectionLoadedTask) descriptor);
        } else {
            throw new RuntimeException("Filter cannot be applied to descriptor: " + descriptor.getClass().getSimpleName());
        }

    }

    private boolean filter(AbstractReflectionLoadedTask reflectionDescriptor) {
        if (TemplateLoadedChangeUnit.class.isAssignableFrom(reflectionDescriptor.getClass())) {
            return filterTemplateChangeUnit((TemplateLoadedChangeUnit) reflectionDescriptor);

        } else if (CodeLoadedChangeUnit.class.isAssignableFrom(reflectionDescriptor.getClass())) {
            return filterCodeChangeUnit((CodeLoadedChangeUnit) reflectionDescriptor);

        } else {
            String message = String.format(
                    "Non-Filterable task[%s]: %s",
                    reflectionDescriptor.getSourceClass(),
                    reflectionDescriptor.getClass());
            throw new RuntimeException(message);
        }

    }

    private boolean filterTemplateChangeUnit(TemplateLoadedChangeUnit reflectionDescriptor) {
        return filterProfiles(reflectionDescriptor.getProfiles());
    }


    private boolean filterCodeChangeUnit(CodeLoadedChangeUnit reflectionDescriptor) {
        Class<?> sourceClass = reflectionDescriptor.getSourceClass();
        if (!sourceClass.isAnnotationPresent(Profile.class)) {
            return true; // no-profiled changeset always matches
        }
        List<String> taskProfile = Arrays.asList(sourceClass.getAnnotation(Profile.class).value());
        return filterProfiles(taskProfile);
    }

    private boolean filterProfiles(List<String> taskProfile) {
        boolean taskHasAtLeastOneProfileApplied = false;
        for (String profile : taskProfile) {
            if ((profile == null || "".equals(profile))) {
                continue;
            }
            if (isNegativeProfile(profile)) {
                if (containsNegativeProfile(activeProfiles, profile)) {
                    return false;
                }
            } else {
                taskHasAtLeastOneProfileApplied = true;
                if (activeProfiles.contains(profile)) {
                    return true;
                }
            }
        }
        return !taskHasAtLeastOneProfileApplied;
    }

    private boolean containsNegativeProfile(List<String> activeProfiles, String profile) {
        return isNegativeProfile(profile) && activeProfiles.contains(profile.substring(1));
    }

}
