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

import io.flamingock.internal.core.change.filter.ChangeFilter;
import io.flamingock.internal.core.change.loaded.AbstractLoadedChange;
import io.flamingock.internal.core.change.loaded.AbstractReflectionLoadedChange;
import io.flamingock.internal.core.change.loaded.AbstractTemplateLoadedChange;
import io.flamingock.internal.core.change.loaded.CodeLoadedChange;
import org.springframework.context.annotation.Profile;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class SpringbootProfileFilter implements ChangeFilter {

    private final List<String> activeProfiles;

    public SpringbootProfileFilter(String... activeProfiles) {
        this.activeProfiles = activeProfiles.length > 0 ? Arrays.asList(activeProfiles) : Collections.emptyList();
    }

    private static boolean isNegativeProfile(String profile) {
        return profile.charAt(0) == '!';
    }

    @Override
    public boolean filter(AbstractLoadedChange descriptor) {
        if (AbstractReflectionLoadedChange.class.isAssignableFrom(descriptor.getClass())) {
            return filter((AbstractReflectionLoadedChange) descriptor);
        } else {
            throw new RuntimeException("Filter cannot be applied to descriptor: " + descriptor.getClass().getSimpleName());
        }

    }

    private boolean filter(AbstractReflectionLoadedChange reflectionDescriptor) {
        if (AbstractTemplateLoadedChange.class.isAssignableFrom(reflectionDescriptor.getClass())) {
            return filterTemplateChange((AbstractTemplateLoadedChange) reflectionDescriptor);

        } else if (CodeLoadedChange.class.isAssignableFrom(reflectionDescriptor.getClass())) {
            return filterCodeChange((CodeLoadedChange) reflectionDescriptor);

        } else {
            String message = String.format(
                    "Non-Filterable change[%s]: %s",
                    reflectionDescriptor.getImplementationClass(),
                    reflectionDescriptor.getClass());
            throw new RuntimeException(message);
        }

    }

    private boolean filterTemplateChange(AbstractTemplateLoadedChange reflectionDescriptor) {
        return filterProfiles(reflectionDescriptor.getProfiles());
    }


    private boolean filterCodeChange(CodeLoadedChange change) {
        // Legacy Mongock @ChangeSet changes: each @ChangeSet method is its own atomic change,
        // so method-level @Profile is the natural gate. We detect the annotation by FQCN.
        //
        // This is intentionally narrow: only methods annotated with the legacy Mongock
        // @ChangeSet annotation (com.github.cloudyrock.mongock.ChangeSet) qualify.
        // Mongock @ChangeUnit / @Execution / @BeforeExecution flows are excluded.
        // Native Flamingock @Apply method-level @Profile is also excluded.
        if (hasChangeSetAnnotation(change.getApplyMethod())) {
            Method applyMethod = change.getApplyMethod();
            if (applyMethod != null && applyMethod.isAnnotationPresent(Profile.class)) {
                return filterProfiles(Arrays.asList(applyMethod.getAnnotation(Profile.class).value()));
            }
        }

        // Native Flamingock changes (and legacy fallback): profiles are declared at the change
        // (class) level only. The change is the atomic unit, so its profile gate lives on the
        // @Change-annotated class. Method-level @Profile (on @Apply or @Rollback) is
        // intentionally NOT honored: a per-method gate is incoherent for a change-level inclusion
        // decision and would risk applying a change while silently skipping its rollback under a
        // different active profile, breaking recovery semantics.
        Class<?> sourceClass = change.getImplementationClass();
        if (!sourceClass.isAnnotationPresent(Profile.class)) {
            return true; // no-profiled changeset always matches
        }
        List<String> changeProfile = Arrays.asList(sourceClass.getAnnotation(Profile.class).value());
        return filterProfiles(changeProfile);
    }

    /**
     * Checks whether the given method carries the legacy Mongock {@code @ChangeSet} annotation,
     * using its fully qualified name to avoid a compile-time dependency on the legacy module.
     */
    private static boolean hasChangeSetAnnotation(Method method) {
        if (method == null) {
            return false;
        }
        for (java.lang.annotation.Annotation ann : method.getDeclaredAnnotations()) {
            if ("com.github.cloudyrock.mongock.ChangeSet".equals(ann.annotationType().getName())) {
                return true;
            }
        }
        return false;
    }

    private boolean filterProfiles(List<String> changeProfile) {
        boolean changeHasAtLeastOneProfileApplied = false;
        for (String profile : changeProfile) {
            if ((profile == null || "".equals(profile))) {
                continue;
            }
            if (isNegativeProfile(profile)) {
                if (containsNegativeProfile(activeProfiles, profile)) {
                    return false;
                }
            } else {
                changeHasAtLeastOneProfileApplied = true;
                if (activeProfiles.contains(profile)) {
                    return true;
                }
            }
        }
        return !changeHasAtLeastOneProfileApplied;
    }

    private boolean containsNegativeProfile(List<String> activeProfiles, String profile) {
        return isNegativeProfile(profile) && activeProfiles.contains(profile.substring(1));
    }

}
