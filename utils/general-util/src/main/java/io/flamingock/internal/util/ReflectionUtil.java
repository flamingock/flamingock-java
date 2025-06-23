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
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class ReflectionUtil {
    private ReflectionUtil() {}

    /**
     * Retrieves the actual type arguments used in a class's generic superclass as Class objects.
     * This method traverses the class hierarchy to find the first parameterized superclass
     * and returns its type arguments as Class objects.
     *
     * @param clazz The class to analyze for generic type arguments
     * @return An array of Class objects representing the actual type arguments
     * @throws IllegalStateException If no parameterized superclass can be found in the hierarchy
     * @throws ClassCastException If any type argument is not a Class (e.g., type variable, wildcard)
     */
    @SuppressWarnings("unchecked")
    public static Class<?>[] getActualTypeArguments(Class<?> clazz) {
        if (clazz == null) {
            throw new IllegalArgumentException("Class cannot be null");
        }

        Class<?> currentClass = clazz;
        while (currentClass != null && currentClass != Object.class) {
            // Check superclass for generic type parameters
            Type genericSuperclass = currentClass.getGenericSuperclass();
            if (genericSuperclass instanceof ParameterizedType) {
                ParameterizedType pt = (ParameterizedType) genericSuperclass;
                Type[] typeArgs = pt.getActualTypeArguments();
                Class<?>[] classArgs = new Class<?>[typeArgs.length];
                
                for (int i = 0; i < typeArgs.length; i++) {
                    if (!(typeArgs[i] instanceof Class<?>)) {
                        throw new ClassCastException("Type argument " + typeArgs[i] + " is not a Class");
                    }
                    classArgs[i] = (Class<?>) typeArgs[i];
                }
                
                return classArgs;
            }

            // Check interfaces for generic type parameters
            for (Type genericInterface : currentClass.getGenericInterfaces()) {
                if (genericInterface instanceof ParameterizedType) {
                    ParameterizedType pt = (ParameterizedType) genericInterface;
                    Type[] typeArgs = pt.getActualTypeArguments();
                    Class<?>[] classArgs = new Class<?>[typeArgs.length];
                    
                    for (int i = 0; i < typeArgs.length; i++) {
                        if (!(typeArgs[i] instanceof Class<?>)) {
                            throw new ClassCastException("Type argument " + typeArgs[i] + " is not a Class");
                        }
                        classArgs[i] = (Class<?>) typeArgs[i];
                    }
                    
                    return classArgs;
                }
            }

            currentClass = currentClass.getSuperclass();
        }

        throw new IllegalStateException("Unable to determine generic type arguments from class hierarchy");
    }


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

    /**
     * Collects all instances of a specific annotation type from a class, its superclass hierarchy, and implemented interfaces.
     * This method recursively traverses the inheritance chain and interface hierarchy, collecting declared annotations
     * of the specified type from each level. Only directly declared annotations are collected (not inherited ones).
     * Duplicate annotations from the same class/interface are avoided through cycle detection.
     *
     * @param <A> The annotation type to collect
     * @param clazz The class to start the search from
     * @param annotationType The class object representing the annotation type to collect
     * @return A list containing all found annotations of the specified type, ordered by traversal: starting class,
     *         then superclasses (bottom-up), then interfaces at each level. Returns an empty list if no annotations are found.
     * @throws NullPointerException if clazz or annotationType is null
     */
    public static <A extends Annotation> List<A> findAllAnnotations(Class<?> clazz, Class<A> annotationType) {
        Set<Class<?>> visited = new HashSet<>();
        List<A> result = new ArrayList<>();

        findAllAnnotationsInternal(clazz, annotationType, visited, result);

        return result;
    }

    private static <A extends Annotation> void findAllAnnotationsInternal(Class<?> clazz, Class<A> annotationType, Set<Class<?>> visited, List<A> result) {
        if (clazz == null || clazz == Object.class || !visited.add(clazz)) return;

        A annotation = clazz.getDeclaredAnnotation(annotationType);
        if (annotation != null) {
            result.add(annotation);
        }

        findAllAnnotationsInternal(clazz.getSuperclass(), annotationType, visited, result);

        for (Class<?> iface : clazz.getInterfaces()) {
            findAllAnnotationsInternal(iface, annotationType, visited, result);
        }
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
