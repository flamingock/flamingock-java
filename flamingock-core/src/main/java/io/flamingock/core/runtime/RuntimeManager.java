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

import io.flamingock.commons.utils.Constants;
import io.flamingock.commons.utils.StringUtil;
import io.flamingock.core.api.annotations.NonLockGuarded;
import io.flamingock.core.api.annotations.Nullable;
import io.flamingock.core.api.exception.FlamingockException;
import io.flamingock.core.engine.lock.Lock;
import io.flamingock.core.runtime.dependency.Dependency;
import io.flamingock.core.runtime.dependency.DependencyInjectable;
import io.flamingock.core.runtime.dependency.DependencyInjectableContext;
import io.flamingock.core.runtime.dependency.exception.DependencyInjectionException;
import io.flamingock.core.runtime.proxy.LockGuardProxyFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class RuntimeManager implements DependencyInjectable {

    private static final Logger logger = LoggerFactory.getLogger(RuntimeManager.class);
    private static final Function<Parameter, String> parameterNameProvider = parameter -> parameter.isAnnotationPresent(Named.class)
            ? parameter.getAnnotation(Named.class).value()
            : null;
    private final Set<Class<?>> nonProxyableTypes = Collections.emptySet();
    private final DependencyInjectableContext dependencyContext;
    private final LockGuardProxyFactory proxyFactory;
    private final boolean isNativeImage;

    private RuntimeManager(LockGuardProxyFactory proxyFactory,
                           DependencyInjectableContext dependencyContext,
                           boolean isNativeImage) {
        this.dependencyContext = dependencyContext;
        this.proxyFactory = proxyFactory;
        this.isNativeImage = isNativeImage;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static void logMethodWithArguments(String methodName, List<Object> changelogInvocationParameters) {
        String arguments = changelogInvocationParameters.stream()
                .map(RuntimeManager::getParameterType)
                .collect(Collectors.joining(", "));
        logger.debug("method[{}] with arguments: [{}]", methodName, arguments);

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

    @Override
    public void addDependencies(Collection<? extends Dependency> dependencies) {
        dependencyContext.addDependencies(dependencies);
    }

    @Override
    public void addDependency(Dependency dependency) {
        dependencyContext.addDependency(dependency);
    }

    @Override
    public void removeDependencyByRef(Dependency dependency) {
        dependencyContext.removeDependencyByRef(dependency);
    }

    public Object getInstance(Constructor<?> constructor) {
        List<Object> signatureParameters = getSignatureParameters(constructor);
        logMethodWithArguments(constructor.getName(), signatureParameters);
        try {
            return constructor.newInstance(signatureParameters.toArray());
        } catch (Exception e) {
            throw new FlamingockException(e);
        }
    }

    public Object executeMethodWithInjectedDependencies(Object instance, Method method) {
        return executeMethodWithParameters(instance, method, getSignatureParameters(method).toArray());
    }

    public Object executeMethodWithParameters(Object instance, Method method, Object... parameters) {
        try {
            return method.invoke(instance, parameters);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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

        Optional<Dependency> dependencyOptional = (StringUtil.isEmpty(name)
                ? dependencyContext.getDependency(type)
                : dependencyContext.getDependency(name)
        );

        final Dependency dependency;
        if (dependencyOptional.isPresent()) {
            dependency = dependencyOptional.get();
        } else {
            if (parameter.isAnnotationPresent(Nullable.class)) {
                return null;
            } else {
                throw new DependencyInjectionException(type, name);
            }
        }

        boolean lockGuarded = !type.isAnnotationPresent(NonLockGuarded.class)
                && !parameter.isAnnotationPresent(NonLockGuarded.class)
                && !type.isAnnotationPresent(io.changock.migration.api.annotations.NonLockGuarded.class)
                && !parameter.isAnnotationPresent(io.changock.migration.api.annotations.NonLockGuarded.class)
                && !nonProxyableTypes.contains(type)
                && !isNativeImage
                ;

        return dependency.isProxeable() && lockGuarded
                ? proxyFactory.getRawProxy(dependency.getInstance(), type)
                : dependency.getInstance();

    }

    private String getParameterName(Parameter parameter) {
        return parameterNameProvider.apply(parameter);
    }

    public static final class Builder {


        private DependencyInjectableContext dependencyContext;
        private Lock lock;
        private Boolean forceNativeImage = null;

        public Builder setLock(Lock lock) {
            this.lock = lock;
            return this;
        }

        public void setForceNativeImage(Boolean forceNativeImage) {
            this.forceNativeImage = forceNativeImage;
        }

        public Builder setDependencyContext(DependencyInjectableContext dependencyContext) {
            this.dependencyContext = dependencyContext;
            return this;
        }


        public RuntimeManager build() {
            LockGuardProxyFactory proxyFactory = new LockGuardProxyFactory(lock);
            boolean isNativeImage;
            if(forceNativeImage != null) {
                isNativeImage = forceNativeImage;
            } else {
                isNativeImage = isRunningInNativeImage();
            }
            logger.info("Running on native image: {}", isNativeImage);
            return new RuntimeManager(proxyFactory, dependencyContext, isNativeImage);
        }

        private static  boolean isRunningInNativeImage() {
            try {
                return "runtime".equals(System.getProperty("org.graalvm.nativeimage.imagecode"));
            } catch (SecurityException exception) {
                return false;
            }
        }

    }
}
