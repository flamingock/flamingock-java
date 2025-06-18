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

package io.flamingock.internal.util;

import org.reflections.Reflections;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class ReflectionUtil {
    private ReflectionUtil() {}


    @SuppressWarnings("unchecked")
    public static Optional<Method> findFirstAnnotatedMethod(Class<?> source, Class<? extends Annotation> annotation) {
        return Arrays.stream(source.getMethods())
                .filter(method -> method.isAnnotationPresent(annotation))
                .findFirst();
    }

    //TODO expand this beyond ChangeUnit
    @SuppressWarnings("unchecked")
    public static Collection<Class<?>> loadAnnotatedClassesFromPackage(String packagePath, Class<? extends Annotation>... annotations) {
        Reflections reflections = new Reflections(packagePath);
        return Stream.of(annotations)
                .map(reflections::getTypesAnnotatedWith)
                .flatMap(Set::stream)
                .collect(Collectors.toList());
    }

    public static List<Class<?>> getParameters(Executable executable) {
        return Arrays.asList(executable.getParameterTypes());
    }

    public static List<Constructor<?>> getAnnotatedConstructors(Class<?> source, Class<? extends Annotation> annotationClass) {
        return getConstructors(source)
                .stream()
                .filter(constructor -> isConstructorAnnotationPresent(constructor, annotationClass))
                .collect(Collectors.toList());
    }

    public static Constructor<?> getConstructorWithAnnotationPreference(Class<?> source, Class<? extends Annotation> annotationClass) {
        List<Constructor<?>> annotatedConstructors = ReflectionUtil.getAnnotatedConstructors(source, annotationClass);
        if (annotatedConstructors.size() == 1) {
            return annotatedConstructors.get(0);
        } else if (annotatedConstructors.size() > 1) {
            throw new MultipleAnnotatedConstructorsFound();
        }
        Constructor<?>[] constructors = source.getConstructors();
        if (constructors.length == 0) {
            throw new ConstructorNotFound();
        }
        if (constructors.length > 1) {
            throw new MultipleConstructorsFound();
        }
        return constructors[0];
    }


    public static List<Constructor<?>> getConstructors(Class<?> source) {
        return Arrays.stream(source.getConstructors())
                .collect(Collectors.toList());
    }

    private static boolean isConstructorAnnotationPresent(Constructor<?> constructor, Class<? extends Annotation> annotationClass) {
        return constructor.isAnnotationPresent(annotationClass) ;
    }


    public static class ConstructorNotFound extends RuntimeException {
    }

    public static class MultipleAnnotatedConstructorsFound extends RuntimeException {
    }

    public static class MultipleConstructorsFound extends RuntimeException {
    }
}
