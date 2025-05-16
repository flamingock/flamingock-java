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

package io.flamingock.core.utils;

import io.flamingock.commons.utils.ReflectionUtil;
import io.flamingock.core.api.annotations.Change;
import io.flamingock.core.api.annotations.NonLockGuarded;
import io.flamingock.core.api.annotations.NonLockGuardedType;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class ExecutionUtils {

    private static final Class<Change> CHANGE_UNIT_CLASS = Change.class;

    private static final Class<io.mongock.api.annotations.ChangeUnit> LEGACY_CHANGE_UNIT_CLASS = io.mongock.api.annotations.ChangeUnit.class;


    private ExecutionUtils() {
    }

    @SuppressWarnings("unchecked")
    public static Collection<Class<?>> loadExecutionClassesFromPackage(String packagePath) {
        return ReflectionUtil.loadAnnotatedClassesFromPackage(packagePath, ExecutionUtils.CHANGE_UNIT_CLASS, ExecutionUtils.LEGACY_CHANGE_UNIT_CLASS);
    }

    /**
     * We need this generic method, although currently it only calls `isChangeUnit`, but in the future there will
     * more annotations, so `isChangeUnit` will be only one of them.
     */
    public static boolean isExecutableClass(Class<?> clazz) {
        return isChangeUnit(clazz);
    }

    public static boolean isChangeUnit(Class<?> clazz) {
        return clazz.isAnnotationPresent(CHANGE_UNIT_CLASS) || clazz.isAnnotationPresent(LEGACY_CHANGE_UNIT_CLASS);
    }

    public static boolean isNewChangeUnit(Class<?> clazz) {
        return clazz.isAnnotationPresent(CHANGE_UNIT_CLASS);
    }

    public static boolean isLegacyChangeUnit(Class<?> clazz) {
        return clazz.isAnnotationPresent(LEGACY_CHANGE_UNIT_CLASS);
    }

    public static boolean isNotLockGuardAnnotated(Class<?> type) {
        return !type.isAnnotationPresent(NonLockGuarded.class) && !type.isAnnotationPresent(io.changock.migration.api.annotations.NonLockGuarded.class);
    }

    public static List<NonLockGuardedType> getLockGuardedTypeFromMethod(Method method) {
        NonLockGuarded nonLockGuardedAnnotation = method.getAnnotation(NonLockGuarded.class);
        if (nonLockGuardedAnnotation != null) {
            return Arrays.asList(nonLockGuardedAnnotation.value());
        } else {
            io.changock.migration.api.annotations.NonLockGuarded legacyNonLockGuardedAnnotation = method.getAnnotation(io.changock.migration.api.annotations.NonLockGuarded.class);
            if (legacyNonLockGuardedAnnotation != null) {
                return Stream.of(legacyNonLockGuardedAnnotation.value())
                        .map(legacyValue -> {
                            switch (legacyValue) {
                                case METHOD:
                                    return NonLockGuardedType.METHOD;
                                case RETURN:
                                    return NonLockGuardedType.RETURN;
                                default:
                                    return NonLockGuardedType.NONE;
                            }
                        }).collect(Collectors.toList());
            } else {
                return Collections.emptyList();
            }
        }
    }

}
