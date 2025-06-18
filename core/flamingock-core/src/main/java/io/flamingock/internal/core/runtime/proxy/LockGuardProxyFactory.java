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

package io.flamingock.internal.core.runtime.proxy;

import io.flamingock.internal.util.Constants;
import io.flamingock.internal.util.JdkUtil;
import io.flamingock.internal.core.engine.lock.Lock;
import io.flamingock.internal.core.utils.ExecutionUtils;
import javassist.util.proxy.ProxyFactory;
import org.objenesis.ObjenesisStd;

import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class LockGuardProxyFactory implements GuardProxyFactory {

    private static final Set<String> DEFAULT_NON_GUARDED_METHODS = new HashSet<>(
            Collections.singletonList("finalize")
    );

    static {
        ProxyFactory.nameGenerator = new ProxyFactory.UniqueName() {
            private final String sep = Constants.PROXY_MONGOCK_PREFIX + Integer.toHexString(this.hashCode() & 0xfff) + "_";
            private int counter = 0;

            @Override
            public String get(String classname) {
                return classname + sep + Integer.toHexString(counter++);
            }
        };
    }

    private final Set<Class<?>> nonGuardedTypes;

    public static LockGuardProxyFactory withLock(Lock lock) {
        return new LockGuardProxyFactory(lock);
    }

    public static LockGuardProxyFactory withLockAndNonGuardedClasses(Lock lock, Set<Class<?>> nonGuardedTypes) {
        return new LockGuardProxyFactory(lock, Collections.emptyList(), nonGuardedTypes, Collections.emptySet());

    }

    private final Lock lock;
    private final Collection<String> notProxiedPackagePrefixes;
    private final Set<String> nonGuardedMethods;

    private LockGuardProxyFactory(Lock lock) {
        this(lock, Collections.emptyList(), Collections.emptySet(), DEFAULT_NON_GUARDED_METHODS);
    }

    private LockGuardProxyFactory(Lock lock,
                                  Collection<String> notProxiedPackagePrefixes,
                                  Set<Class<?>> nonGuardedTypes,
                                  Set<String> nonGuardedMethods) {
        this.lock = lock;
        this.notProxiedPackagePrefixes = notProxiedPackagePrefixes != null ? notProxiedPackagePrefixes : Collections.emptySet();
        this.nonGuardedTypes = nonGuardedTypes != null ? nonGuardedTypes : Collections.emptySet();
        this.nonGuardedMethods = nonGuardedMethods != null ? nonGuardedMethods : Collections.emptySet();
    }

    @SuppressWarnings("unchecked")
    public <T> T getProxy(T targetObject, Class<? super T> interfaceType) {
        return (T) getRawProxy(targetObject, interfaceType);
    }

    public Object getRawProxy(Object targetObject, Class<?> interfaceType) {
        return shouldBeLockGuardProxied(targetObject, interfaceType) ? createProxy(targetObject, interfaceType) : targetObject;
    }

    private boolean shouldBeLockGuardProxied(Object targetObject, Class<?> interfaceType) {
        return targetObject != null
                && !isTargetInstanceOfNonGuardedTypes(targetObject)
                && !Modifier.isFinal(interfaceType.getModifiers())
                && isPackageProxiable(interfaceType.getPackage().getName())
                && ExecutionUtils.isNotLockGuardAnnotated(interfaceType)
                && ExecutionUtils.isNotLockGuardAnnotated(targetObject.getClass())
                && !JdkUtil.isInternalJdkClass(targetObject.getClass())
                && !JdkUtil.isInternalJdkClass(interfaceType);
    }

    private boolean isTargetInstanceOfNonGuardedTypes(Object targetObject) {
        return nonGuardedTypes.stream()
                .anyMatch(nonGuardedType -> nonGuardedType.isAssignableFrom(targetObject.getClass()));
    }

    private boolean isPackageProxiable(String packageName) {
        return notProxiedPackagePrefixes.stream().noneMatch(packageName::startsWith);
    }

    private Object createProxy(Object impl, Class<?> type) {

        ProxyFactory proxyFactory = new ProxyFactory();
        if (type.isInterface()) {
            proxyFactory.setInterfaces(new Class<?>[]{type});
        } else {
            proxyFactory.setSuperclass(type);
        }

        Object proxyInstance = new ObjenesisStd()
                .getInstantiatorOf(proxyFactory.createClass())
                .newInstance();

        ((javassist.util.proxy.Proxy) proxyInstance).setHandler(new LockGuardMethodHandler<>(impl, lock, this, nonGuardedMethods));
        return proxyInstance;
    }

    public static boolean isProxy(Object obj) {
        return isProxyClass(obj.getClass());
    }

    public static boolean isProxyClass(Class<?> c) {
        return Proxy.isProxyClass(c) || ProxyFactory.isProxyClass(c);
    }

    public static void checkProxy(Object obj) {
        if (!isProxyClass(obj.getClass())) {
            throw new RuntimeException("Is not proxy");
        }
    }

}
