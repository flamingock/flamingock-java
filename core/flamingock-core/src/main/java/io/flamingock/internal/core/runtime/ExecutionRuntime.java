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
package io.flamingock.internal.core.runtime;

import io.flamingock.api.annotations.NonLockGuarded;
import io.flamingock.api.annotations.Nullable;
import io.flamingock.internal.common.core.context.Context;
import io.flamingock.internal.common.core.context.ContextResolver;
import io.flamingock.internal.common.core.context.Dependency;
import io.flamingock.internal.common.core.context.InjectableContextProvider;
import io.flamingock.internal.common.core.error.FlamingockException;
import io.flamingock.internal.core.context.PriorityContext;
import io.flamingock.internal.core.context.SimpleContext;
import io.flamingock.internal.core.engine.lock.Lock;
import io.flamingock.internal.core.runtime.proxy.LockGuardProxyFactory;
import io.flamingock.internal.util.Constants;
import io.flamingock.internal.util.StringUtil;
import io.flamingock.internal.util.FlamingockLoggerFactory;
import org.slf4j.Logger;

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
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class ExecutionRuntime implements InjectableContextProvider {

    private static final Logger logger = FlamingockLoggerFactory.getLogger("Runtime");
    private static final Function<Parameter, String> parameterNameProvider = parameter -> parameter.isAnnotationPresent(Named.class)
            ? parameter.getAnnotation(Named.class).value()
            : null;
    private String sessionId;
    private final Set<Class<?>> nonProxyableTypes = Collections.emptySet();
    private final Context dependencyContext;
    private final LockGuardProxyFactory proxyFactory;
    private final boolean isNativeImage;

    private ExecutionRuntime(String sessionId,
                             LockGuardProxyFactory proxyFactory,
                             Context baseContext,
                             boolean isNativeImage) {
        this.sessionId = sessionId;
        this.dependencyContext = new PriorityContext(new SimpleContext(), baseContext);
        this.proxyFactory = proxyFactory;
        this.isNativeImage = isNativeImage;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public ContextResolver getContext() {
        return dependencyContext;
    }

    public String getSessionId() {
        return sessionId;
    }

    @Override
    public void addDependencies(Collection<? extends Dependency> dependencies) {
        dependencyContext.addDependencies(dependencies);
    }

    @Override
    public void addDependency(Dependency dependency) {
        dependencyContext.addDependency(dependency);
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
                throw new MissingInjectedParameterException(type, name);
            }
        }

        boolean lockGuarded = !type.isAnnotationPresent(NonLockGuarded.class)
                && !parameter.isAnnotationPresent(NonLockGuarded.class)
                && !nonProxyableTypes.contains(type)
                && !isNativeImage;

        return dependency.isProxeable() && lockGuarded
                ? proxyFactory.getRawProxy(dependency.getInstance(), type)
                : dependency.getInstance();

    }

    private String getParameterName(Parameter parameter) {
        return parameterNameProvider.apply(parameter);
    }

    private static void logMethodWithArguments(String methodName, List<Object> changelogInvocationParameters) {
        String arguments = changelogInvocationParameters.stream()
                .map(ExecutionRuntime::getParameterType)
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

    public static final class Builder {
        private String sessionId;
        private Context dependencyContext;
        private LockGuardProxyFactory lockProxyFactory;
        private Lock lock;
        private Boolean forceNativeImage = null;
        private Set<Class<?>> nonGuardedTypes;

        public Builder setSessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public Builder setLock(Lock lock) {
            this.lock = lock;
            return this;
        }

        public Builder setLockGuardProxyFactory(LockGuardProxyFactory proxyFactory) {
            this.lockProxyFactory = proxyFactory;
            return this;
        }

        public Builder setNonGuardedTypes(Set<Class<?>> nonGuardedTypes) {
            this.nonGuardedTypes = nonGuardedTypes;
            return this;
        }

        public void setForceNativeImage(Boolean forceNativeImage) {
            this.forceNativeImage = forceNativeImage;
        }

        public Builder setDependencyContext(Context dependencyContext) {
            this.dependencyContext = dependencyContext;
            return this;
        }


        public ExecutionRuntime build() {
            LockGuardProxyFactory proxyFactory = this.lockProxyFactory != null
                    ? this.lockProxyFactory
                    : LockGuardProxyFactory.withLockAndNonGuardedClasses(lock, nonGuardedTypes);
            boolean isNativeImage;
            if (forceNativeImage != null) {
                isNativeImage = forceNativeImage;
            } else {
                isNativeImage = isRunningInNativeImage();
            }
            sessionId = sessionId != null ? sessionId : UUID.randomUUID().toString();
            logger.debug("Running on native image: {}", isNativeImage);
            return new ExecutionRuntime(sessionId, proxyFactory, dependencyContext, isNativeImage);
        }

        private static boolean isRunningInNativeImage() {
            try {
                return "runtime".equals(System.getProperty("org.graalvm.nativeimage.imagecode"));
            } catch (SecurityException exception) {
                return false;
            }
        }

    }
}
