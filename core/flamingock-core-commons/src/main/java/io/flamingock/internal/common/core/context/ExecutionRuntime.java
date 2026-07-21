package io.flamingock.internal.common.core.context;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

public interface ExecutionRuntime extends ExecutionContext {

    Object getInstance(Constructor<?> constructor);

    Object executeMethodWithInjectedDependencies(Object instance, Method method);

    Object executeMethodWithParameters(Object instance, Method method, Object... parameters);
}
