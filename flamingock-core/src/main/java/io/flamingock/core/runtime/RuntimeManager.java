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

package io.flamingock.core.runtime;

import io.changock.migration.api.annotations.NonLockGuarded;
import io.flamingock.core.api.annotations.ChangeUnitConstructor;
import io.flamingock.core.api.annotations.FlamingockConstructor;
import io.flamingock.core.api.exception.FlamingockException;
import io.flamingock.core.engine.lock.Lock;
import io.flamingock.core.runtime.dependency.Dependency;
import io.flamingock.core.runtime.dependency.DependencyInjectable;
import io.flamingock.core.runtime.dependency.DependencyInjectableContext;
import io.flamingock.core.runtime.dependency.exception.DependencyInjectionException;
import io.flamingock.core.runtime.proxy.LockGuardProxyFactory;
import io.flamingock.commons.utils.Constants;
import io.flamingock.commons.utils.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class RuntimeManager implements DependencyInjectable {

    private static final Logger logger = LoggerFactory.getLogger(RuntimeManager.class);


    public static Builder builder() {
        return new Builder();
    }

    private static final Function<Parameter, String> parameterNameProvider = parameter -> parameter.isAnnotationPresent(Named.class)
            ? parameter.getAnnotation(Named.class).value()
            : null;
    private final Set<Class<?>> nonProxyableTypes = Collections.emptySet();
    private final DependencyInjectableContext dependencyContext;
    private final LockGuardProxyFactory proxyFactory;

    public RuntimeManager(LockGuardProxyFactory proxyFactory,
                          DependencyInjectableContext dependencyContext) {
        this.dependencyContext = dependencyContext;
        this.proxyFactory = proxyFactory;
    }

    @Override
    public void addDependencies(Collection<? extends Dependency> dependencies) {
        dependencyContext.addDependencies(dependencies);
    }

    @Override
    public void addDependency(Dependency dependency) {
        dependencyContext.addDependency(dependency);
    }

    public Object getInstance(Class<?> type) {
        Constructor<?> constructor = getConstructor(type);
        List<Object> signatureParameters = getSignatureParameters(constructor);
        logMethodWithArguments(constructor.getName(), signatureParameters);
        try {
            return constructor.newInstance(signatureParameters.toArray());
        } catch (Exception e) {
            throw new FlamingockException(e);
        }
    }



    public Object executeMethod(Object instance, Method method) {
        List<Object> signatureParameters = getSignatureParameters(method);
        try {
            return method.invoke(instance, signatureParameters.toArray());
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    private Constructor<?> getConstructor(Class<?> type) {

        List<Constructor<?>> annotatedConstructors = Arrays.stream(type.getConstructors())
                .filter(RuntimeManager::isConstructorAnnotationPresent)
                .collect(Collectors.toList());
        if (annotatedConstructors.size() == 1) {
            return annotatedConstructors.get(0);

        } else if (annotatedConstructors.size() == 0) {
            logger.debug("Not found constructor for class[{}] annotated with {}", type.getName(), FlamingockConstructor.class.getSimpleName());
            Constructor<?>[] constructors = type.getConstructors();
            if (constructors.length == 0) {
                throw new FlamingockException("Cannot find a valid constructor for class[%s]", type.getName());
            }
            if (constructors.length > 1) {
                throw new FlamingockException("Found multiple constructors without annotation %s  for class[%s].\n" +
                        "When more than one constructor, exactly one of them must be annotated. And it will be taken as default "
                        , FlamingockConstructor.class.getSimpleName()
                        , type.getName()
                );
            }
            return constructors[0];

        } else {
            //annotatedConstructors.size() > 1
            throw new FlamingockException("Found multiple constructors for class[%s] annotated with %s." +
                    " Annotate the one you want Mongock to use to instantiate your changeUnit",
                    type.getName(),
                    FlamingockConstructor.class.getSimpleName());
        }
    }

    private <T> Constructor<T> getTypedConstructor(Class<T> type) {

        List<Constructor<T>> annotatedConstructors = Arrays.stream(type.getConstructors())
                .filter(RuntimeManager::isConstructorAnnotationPresent)
                .map(constructor-> (Constructor<T>)constructor)
                .collect(Collectors.toList());
        if (annotatedConstructors.size() == 1) {
            return annotatedConstructors.get(0);

        } else if (annotatedConstructors.size() == 0) {
            logger.debug("Not found constructor for class[{}] annotated with {}", type.getName(), FlamingockConstructor.class.getSimpleName());
            List<Constructor<T>> constructors = Arrays.stream(type.getConstructors())
                    .map(constructor-> (Constructor<T>)constructor)
                    .collect(Collectors.toList());
            if (constructors.size() == 0) {
                throw new FlamingockException("Cannot find a valid constructor for class[%s]", type.getName());
            }
            if (constructors.size() > 1) {
                throw new FlamingockException("Found multiple constructors without annotation %s  for class[%s].\n" +
                        "When more than one constructor, exactly one of them must be annotated. And it will be taken as default "
                        , FlamingockConstructor.class.getSimpleName()
                        , type.getName()
                );
            }
            return constructors.get(0);

        } else {
            //annotatedConstructors.size() > 1
            throw new FlamingockException("Found multiple constructors for class[%s] annotated with %s." +
                    " Annotate the one you want Mongock to use to instantiate your changeUnit",
                    type.getName(),
                    FlamingockConstructor.class.getSimpleName());
        }
    }


    private static boolean isConstructorAnnotationPresent(Constructor<?> constructor) {
        return constructor.isAnnotationPresent(FlamingockConstructor.class) || constructor.isAnnotationPresent(ChangeUnitConstructor.class);
    }

    private List<Object> getSignatureParameters(Executable executable) {
        Class<?>[] parameterTypes = executable.getParameterTypes();
        Parameter[] parameters = executable.getParameters();
        List<Object> signatureParameters = new ArrayList<>(parameterTypes.length);
        for (int paramIndex = 0; paramIndex < parameterTypes.length; paramIndex++) {
            signatureParameters.add(getParameter(parameterTypes[paramIndex], parameters[paramIndex]));
        }
        return signatureParameters;
    }

    private Object getParameter(Class<?> type, Parameter parameter) {
        String name = getParameterName(parameter);

        Dependency dependency = (StringUtil.isEmpty(name)
                ? dependencyContext.getDependency(type)
                : dependencyContext.getDependency(name)
        ).orElseThrow(() -> new DependencyInjectionException(type, name));

        boolean lockGuarded = !type.isAnnotationPresent(NonLockGuarded.class)
                && !parameter.isAnnotationPresent(NonLockGuarded.class)
                && !nonProxyableTypes.contains(type);

        return dependency.isProxeable() && lockGuarded
                ? proxyFactory.getRawProxy(dependency.getInstance(), dependency.getType())
                : dependency.getInstance();

    }

    private String getParameterName(Parameter parameter) {
        return parameterNameProvider.apply(parameter);
    }


    public static void logMethodWithArguments(String methodName, List<Object> changelogInvocationParameters) {
        String arguments = changelogInvocationParameters.stream()
                .map(RuntimeManager::getParameterType)
                .collect(Collectors.joining(", "));
        logger.info("method[{}] with arguments: [{}]", methodName, arguments);

    }

    private static String getParameterType(Object obj) {
        String className = obj != null ? obj.getClass().getName() : "{null argument}";
        int mongockProxyPrefixIndex = className.indexOf(Constants.PROXY_MONGOCK_PREFIX);
        if (mongockProxyPrefixIndex > 0) {
            return className.substring(0, mongockProxyPrefixIndex);
        } else {
            return className;
        }
    }

    public static final class Builder {

        private DependencyInjectableContext dependencyContext;
        private Lock lock;

        public Builder setLock(Lock lock) {
            this.lock = lock;
            return this;
        }

        public Builder setDependencyContext(DependencyInjectableContext dependencyContext) {
            this.dependencyContext = dependencyContext;
            return this;
        }

        public RuntimeManager build() {
            LockGuardProxyFactory proxyFactory = new LockGuardProxyFactory(lock);
            return new RuntimeManager(proxyFactory, dependencyContext);
        }
    }
}
