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

import io.flamingock.core.api.annotations.NonLockGuardedType;
import io.flamingock.internal.core.engine.lock.Lock;
import io.flamingock.internal.core.utils.ExecutionUtils;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.List;
import java.util.Set;

public class LockGuardProxy<T> implements InvocationHandler {

    private final Lock lockEnsurer;
    private final T implementation;
    private final LockGuardProxyFactory proxyFactory;
    private final Set<String> nonGuardedMethods;

    public LockGuardProxy(T implementation, Lock lock, LockGuardProxyFactory proxyFactory, Set<String> nonGuardedMethods) {
        this.implementation = implementation;
        this.lockEnsurer = lock;
        this.proxyFactory = proxyFactory;
        this.nonGuardedMethods = nonGuardedMethods;
    }


    private static boolean shouldTryProxyReturn(List<NonLockGuardedType> methodNoGuardedLockTypes, Type type) {
        return !(type instanceof TypeVariable && ((TypeVariable<?>) type).getGenericDeclaration() != null)
                && !methodNoGuardedLockTypes.contains(NonLockGuardedType.RETURN)
                && !methodNoGuardedLockTypes.contains(NonLockGuardedType.NONE);
    }

    private boolean shouldMethodBeLockGuarded(Method method, List<NonLockGuardedType> noGuardedLockTypes) {
        return !nonGuardedMethods.contains(method.getName())
                && !noGuardedLockTypes.contains(NonLockGuardedType.METHOD)
                && !noGuardedLockTypes.contains(NonLockGuardedType.NONE);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        List<NonLockGuardedType> noGuardedLockTypes = ExecutionUtils.getLockGuardedTypeFromMethod(method);

        if (shouldMethodBeLockGuarded(method, noGuardedLockTypes)) {
            lockEnsurer.ensure();
        }

        return shouldTryProxyReturn(noGuardedLockTypes, method.getGenericReturnType())
                ? proxyFactory.getRawProxy(method.invoke(implementation, args), method.getReturnType())
                : method.invoke(implementation, args);
    }
}
