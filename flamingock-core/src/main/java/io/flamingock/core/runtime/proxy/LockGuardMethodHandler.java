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

package io.flamingock.core.runtime.proxy;

import io.flamingock.core.engine.lock.Lock;
import javassist.util.proxy.MethodHandler;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Set;

public class LockGuardMethodHandler<T> implements MethodHandler {

    private final LockGuardProxy<T> lockGuardProxy;

    public LockGuardMethodHandler(T implementation, Lock lockEnsurer, LockGuardProxyFactory proxyFactory, Set<String> nonGuardedMethods) {
        this.lockGuardProxy = new LockGuardProxy<>(implementation, lockEnsurer, proxyFactory, nonGuardedMethods);
    }

    public LockGuardProxy<T> getLockGuardProxy() {
        return lockGuardProxy;
    }

    @Override
    public Object invoke(Object proxy, Method method, Method method1, Object[] methodArgs) throws Throwable {
        try {
            return lockGuardProxy.invoke(proxy, method, methodArgs);
        } catch (InvocationTargetException ex) {
            if (ex.getTargetException() != null) {
                throw ex.getTargetException();
            }
            throw ex;
        }
    }
}
