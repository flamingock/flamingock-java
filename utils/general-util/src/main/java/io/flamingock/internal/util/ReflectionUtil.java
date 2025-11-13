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
package io.flamingock.internal.util;

import org.reflections.Reflections;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

    /**
     * Resolves the type arguments that {@code concreteClass} supplies for the generic superclass or interface {@code targetGeneric}.
     *
     * @throws IllegalArgumentException if {@code targetGeneric} is not in the hierarchy of {@code concreteClass}.
     */
    public static Type[] resolveTypeArguments(Class<?> concreteClass, Class<?> targetGeneric) {
        Objects.requireNonNull(concreteClass, "concreteClass");
        Objects.requireNonNull(targetGeneric, "targetGeneric");
        Map<TypeVariable<?>, Type> assigns = new HashMap<>();
        Type[] result = resolveUpwards(concreteClass, targetGeneric, assigns);
        if (result == null) {
            throw new IllegalArgumentException(
                    "The target type " + targetGeneric.getName() + " is not in the hierarchy of " + concreteClass.getName());
        }
        return result;
    }

    /** Convenience overload: uses the runtime class of the given instance. */
    public static Type[] resolveTypeArguments(Object instance, Class<?> targetGeneric) {
        return resolveTypeArguments(instance.getClass(), targetGeneric);
    }

    /** Variant returning raw classes (defaults to Object.class if resolution fails). */
    public static Class<?>[] resolveTypeArgumentsAsClasses(Class<?> concreteClass, Class<?> targetGeneric) {
        Type[] types = resolveTypeArguments(concreteClass, targetGeneric);
        Class<?>[] classes = new Class<?>[types.length];
        for (int i = 0; i < types.length; i++) {
            classes[i] = toClass(types[i]);
            if (classes[i] == null) classes[i] = Object.class;
        }
        return classes;
    }

    /**
     * Searches the hierarchy (both classes and interfaces) for a path to {@code targetGeneric}.
     * At each step, binds the type variables of the raw supertype to the actual arguments in the current context.
     * Returns the final Types corresponding to the type parameters of {@code targetGeneric}, or null if no path exists.
     */
    private static Type[] resolveUpwards(Class<?> current, Class<?> targetGeneric,
                                         Map<TypeVariable<?>, Type> assigns) {
        if (current == null || current == Object.class) return null;

        if (current == targetGeneric) {
            // We are exactly at the target generic class/interface: resolve its own type parameters
            TypeVariable<?>[] params = current.getTypeParameters();
            Type[] out = new Type[params.length];
            for (int i = 0; i < params.length; i++) {
                out[i] = resolve(params[i], assigns);
            }
            return out;
        }

        // 1) Check generic superclass
        Type superType = current.getGenericSuperclass();
        Type[] viaSuper = tryAscend(superType, targetGeneric, assigns);
        if (viaSuper != null) return viaSuper;

        // 2) Check generic interfaces
        for (Type itf : current.getGenericInterfaces()) {
            Type[] viaItf = tryAscend(itf, targetGeneric, assigns);
            if (viaItf != null) return viaItf;
        }

        // 3) Continue via raw superclass (if non-parameterised) so as not to lose the path
        Class<?> rawSuper = current.getSuperclass();
        return resolveUpwards(rawSuper, targetGeneric, assigns);
    }

    /** Attempts to ascend one step (superclass or interface), extending {@code assigns}, and continues the search. */
    private static Type[] tryAscend(Type superType, Class<?> targetGeneric, Map<TypeVariable<?>, Type> assigns) {
        if (superType == null) return null;

        if (superType instanceof ParameterizedType) {
            ParameterizedType p = (ParameterizedType)superType;
            Class<?> raw = (Class<?>) p.getRawType();
            Map<TypeVariable<?>, Type> next = new HashMap<>(assigns);
            TypeVariable<?>[] params = raw.getTypeParameters();
            Type[] actualTypeArguments = p.getActualTypeArguments();
            for (int i = 0; i < params.length; i++) {
                next.put(params[i], resolve(actualTypeArguments[i], assigns));
            }
            return resolveUpwards(raw, targetGeneric, next);
        } else if (superType instanceof Class<?>) {
            // No type parameters at this step
            Class<?> c = (Class<?>)superType;
            return resolveUpwards(c, targetGeneric, assigns);
        }
        return null;
    }

    /** Recursively resolves a {@link Type} using the accumulated assignments. */
    private static Type resolve(Type t, Map<TypeVariable<?>, Type> assigns) {
        while (t instanceof TypeVariable<?>) {
            TypeVariable<?> tv = (TypeVariable<?>)t;
            Type mapped = assigns.get(tv);
            if (mapped == null) return tv; // Not yet resolved
            t = mapped;
        }
        if (t instanceof WildcardType) {
            WildcardType w = (WildcardType)t;
            Type[] upper = w.getUpperBounds();
            return upper.length > 0 ? resolve(upper[0], assigns) : Object.class;
        }
        if (t instanceof ParameterizedType) {
            // Keep the ParameterizedType — caller can access its raw type or arguments
            return t;
        }
        if (t instanceof GenericArrayType) {
            GenericArrayType ga = (GenericArrayType)t;
            Type comp = resolve(ga.getGenericComponentType(), assigns);
            Class<?> compClass = toClass(comp);
            if (compClass != null) {
                return Array.newInstance(compClass, 0).getClass();
            }
            return ga; // Return generic array type if class cannot be materialised
        }
        return t; // Already a Class<?> or other usable type
    }

    /** Converts a {@link Type} to a {@link Class} where possible; if ParameterizedType, returns its raw type. */
    private static Class<?> toClass(Type t) {
        if (t instanceof Class<?>) return (Class<?>)t;
        if (t instanceof ParameterizedType) return (Class<?>) ((ParameterizedType)t).getRawType();
        if (t instanceof GenericArrayType) {
            GenericArrayType ga = (GenericArrayType)t;
            Class<?> comp = toClass(ga.getGenericComponentType());
            return comp != null ? Array.newInstance(comp, 0).getClass() : null;
        }
        if (t instanceof TypeVariable<?> || t instanceof WildcardType) return Object.class;
        return null;
    }


    @SuppressWarnings("unchecked")
    public static Optional<Method> findFirstAnnotatedMethod(Class<?> source, Class<? extends Annotation> annotation) {
        return Arrays.stream(source.getMethods())
                .filter(method -> method.isAnnotationPresent(annotation))
                .findFirst();
    }

    @SuppressWarnings("unchecked")
    public static Optional<Method> findFirstMethodByName(Class<?> source, String methodName) {
        return Arrays.stream(source.getMethods())
                .filter(method -> method.getName().equals(methodName))
                .findFirst();
    }

    //TODO expand this beyond Change
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

    public static ExecutableElement getConstructorWithAnnotationPreference(TypeElement typeElement, Class<? extends Annotation> annotationClass) {
        List<ExecutableElement> constructors = getConstructors(typeElement);
        if (constructors.isEmpty()) {
            throw new ReflectionUtil.ConstructorNotFound();
        } else if (constructors.size() == 1) {
            return constructors.get(0);
        } else {
            List<ExecutableElement> annotatedConstructors = filterAnnotatedConstructors(constructors, annotationClass);
            if (annotatedConstructors.isEmpty()) {
                throw new ReflectionUtil.MultipleConstructorsFound();
            } else if (annotatedConstructors.size() == 1) {
                return annotatedConstructors.get(0);
            } else {
                throw new ReflectionUtil.MultipleAnnotatedConstructorsFound();
            }
        }
    }

    public static List<ExecutableElement> filterAnnotatedConstructors(List<ExecutableElement> constructorElements, Class<? extends Annotation> annotationClass) {
        return constructorElements
                .stream()
                .filter(constructor -> isConstructorAnnotationPresent(constructor, annotationClass))
                .collect(Collectors.toList());
    }

    public static List<ExecutableElement> getConstructors(TypeElement typeElement) {
        return typeElement.getEnclosedElements()
                .stream()
                .filter(e -> e.getKind() == ElementKind.CONSTRUCTOR)
                .map(e -> (ExecutableElement)e)
                .collect(Collectors.toList());
    }

    private static boolean isConstructorAnnotationPresent(Element constructorElement, Class<? extends Annotation> annotationClass) {
        return constructorElement.getAnnotation(annotationClass) != null;
    }

    public static List<String> getParametersTypesQualifiedNames(ExecutableElement element) {
        List<String> parameterTypes = new ArrayList<>();
        for (VariableElement param : element.getParameters()) {
            TypeMirror paramType = param.asType();
            parameterTypes.add(paramType.toString()); // fully qualified name (e.g., java.lang.String)
        }
        return parameterTypes;
    }


    /**
     * Finds the default constructor of a class
     *
     * @param targetClass       the class where the constructor is declared
     * @return the matching constructor
     */
    public static Constructor<?> getDefaultConstructor(Class<?> targetClass) {
        return getConstructorFromParameterTypeNames(targetClass, Collections.emptyList());
    }

    /**
     * Finds a constructor that matches the provided parameter type names.
     * - If parameterTypeNames is null/empty: return the default (no-arg) constructor,
     *   whether implicit or explicit.
     * - Otherwise: search only declared constructors.
     *   * First, filter by same arity (parameter count).
     *   * If exactly one remains, return it.
     *   * If several remain, require an exact declared match by resolved types.
     *
     * @param targetClass       the class where the constructor is declared
     * @param parameterTypeNames list of parameter type names (e.g., "java.lang.String", "int[]")
     * @return the matching constructor
     */
    public static Constructor<?> getConstructorFromParameterTypeNames(
            Class<?> targetClass,
            List<String> parameterTypeNames) {

        if (targetClass == null) {
            throw new IllegalArgumentException("targetClass cannot be null");
        }

        // Case 1: default constructor (0 parameters)
        if (parameterTypeNames == null || parameterTypeNames.isEmpty()) {
            // Try explicit declared no-arg first
            try {
                return targetClass.getDeclaredConstructor();
            } catch (NoSuchMethodException ignore) {
                // If there is no declared one, try the implicit/public one (inherited allowed)
                try {
                    return targetClass.getConstructor();
                } catch (NoSuchMethodException e) {
                    throw new RuntimeException("No default constructor (implicit or explicit) found in " + targetClass.getName(), e);
                }
            }
        }

        // Case 2: N > 0 parameters: search declared constructors only
        final Constructor<?>[] declared = targetClass.getDeclaredConstructors();
        final int paramCount = parameterTypeNames.size();

        // Filter by arity
        List<Constructor<?>> sameArity = Arrays.stream(declared)
                .filter(c -> c.getParameterCount() == paramCount)
                .collect(Collectors.toList());

        if (sameArity.isEmpty()) {
            throw new RuntimeException("No declared constructor in " + targetClass.getName()
                    + " with parameter count = " + paramCount);
        }
        if (sameArity.size() == 1) {
            return sameArity.get(0);
        }

        final Class<?>[] exactTypes = toClasses(parameterTypeNames);

        try {
            return targetClass.getDeclaredConstructor(exactTypes);
        } catch (NoSuchMethodException e) {
            String sig = "(" + Arrays.stream(exactTypes).map(Class::getTypeName).collect(Collectors.joining(", ")) + ")";
            throw new RuntimeException("No declared constructor in " + targetClass.getName()
                    + " exactly matching parameter types " + sig, e);
        }
    }

    /**
     * Finds a declared method by name using your 3-step heuristic:
     *  1) Filter declared methods by name.
     *     - If exactly one remains, return it.
     *  2) Else, filter by parameter count == parameterTypeNames.size().
     *     - If exactly one remains, return it.
     *  3) Else, resolve the parameter type names to classes and call getDeclaredMethod(...)
     *     for an exact type match. If not found, throw.
     * Notes:
     *  - Only declared methods are considered (no inheritance).
     */
    public static Method getDeclaredMethodFromParameterTypeNames(
            Class<?> targetClass,
            String methodName,
            List<String> parameterTypeNames) {

        if (targetClass == null) {
            throw new RuntimeException("targetClass cannot be null");
        }
        if (methodName == null || methodName.isEmpty()) {
            throw new RuntimeException("methodName cannot be null or empty");
        }

        // Step 1: by name
        List<Method> sameName = Arrays.stream(targetClass.getDeclaredMethods())
                .filter(m -> m.getName().equals(methodName))
                .collect(Collectors.toList());

        if (sameName.isEmpty()) {
            throw new RuntimeException("No declared method named '" + methodName
                    + "' found in " + targetClass.getName());
        }
        else if (sameName.size() == 1) {
            return sameName.get(0);
        }

        // Step 2: by arity
        final int paramCount = (parameterTypeNames == null) ? 0 : parameterTypeNames.size();
        List<Method> sameArity = sameName.stream()
                .filter(m -> m.getParameterCount() == paramCount)
                .collect(Collectors.toList());

        if (sameArity.isEmpty()) {
            throw new RuntimeException("No declared method named '" + methodName
                    + "' with parameter count = " + paramCount
                    + " found in " + targetClass.getName());
        }
        else if (sameArity.size() == 1) {
            return sameArity.get(0);
        }

        // Step 3: exact type match via getDeclaredMethod(...)
        final Class<?>[] exactTypes = toClasses(parameterTypeNames);

        try {
            return targetClass.getDeclaredMethod(methodName, exactTypes);
        } catch (NoSuchMethodException e) {
            String sig = "(" + Arrays.stream(exactTypes).map(Class::getTypeName).collect(Collectors.joining(", ")) + ")";
            throw new RuntimeException("No declared method named '" + methodName
                    + "' exactly matching parameter types " + sig
                    + " in " + targetClass.getName(), e);
        }
    }

    private static Class<?>[] toClasses(List<String> names) {
        if (names == null || names.isEmpty()) return new Class<?>[0];
        Class<?>[] result = new Class<?>[names.size()];
        for (int i = 0; i < names.size(); i++) {
            result[i] = loadType(names.get(i).trim());
        }
        return result;
    }

    private static Class<?> loadType(String typeName) {
        // Direct primitive
        Class<?> primitive = getPrimitiveClassFromName(typeName);
        if (primitive != null) return primitive;

        // Handle array suffix "[]"
        int dims = 0;
        while (typeName.endsWith("[]")) {
            dims++;
            typeName = typeName.substring(0, typeName.length() - 2);
        }

        Class<?> base;
        Class<?> primBase = getPrimitiveClassFromName(typeName);
        if (primBase != null) {
            base = primBase;
        } else {
            try {
                base = Class.forName(typeName);
            }
            catch (ClassNotFoundException ignore) {
                throw new RuntimeException("Class not found for parameter type name: " + typeName);
            }
        }

        if (dims == 0) return base;

        // Build multi-dimensional array type
        int[] zeros = new int[dims];
        Object array = Array.newInstance(base, zeros);
        return array.getClass();
    }

    /**
     * Returns the primitive Class for a given primitive name, or null if not a primitive name.
     * Accepted names: boolean, byte, short, char, int, long, float, double, void
     */
    private static Class<?> getPrimitiveClassFromName(String name) {
        if (name == null) return null;
        switch (name) {
            case "boolean": return boolean.class;
            case "byte":    return byte.class;
            case "short":   return short.class;
            case "char":    return char.class;
            case "int":     return int.class;
            case "long":    return long.class;
            case "float":   return float.class;
            case "double":  return double.class;
            case "void":    return void.class;
            default:        return null;
        }
    }
}
