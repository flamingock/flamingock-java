package io.flamingock.core.runtime;


import io.flamingock.core.runtime.dependency.SimpleDependencyInjectableContext;
import io.flamingock.core.runtime.dependency.exception.DependencyInjectionException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.annotation.Nullable;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RuntimeManagerTest {

    @Test
    @DisplayName("should throw exception when executing method if no dependency and not annotated with @Nullable")
    void shouldThrowExceptionIfNoNullableAnnotation() throws NoSuchMethodException {

        RuntimeManager runtimeManager = RuntimeManager.builder()
                .setDependencyContext(new SimpleDependencyInjectableContext())
                .setLock(null)
                .build();

        Method methodWithNoNullable = RuntimeManagerTest.class.getMethod("methodWithNoNullable", ParameterClass.class);
        RuntimeException ex = Assertions.assertThrows(RuntimeException.class, () -> runtimeManager.executeMethod(new RuntimeManagerTest(), methodWithNoNullable));
        DependencyInjectionException cause = (DependencyInjectionException) ex.getCause();
        assertEquals(ParameterClass.class, cause.getWrongParameter());
    }

    @Test
    @DisplayName("should not throw exception when executing method if no dependency and parameter is annotated with @Nullable")
    void shouldNotThrowExceptionIfNullableAnnotation() throws NoSuchMethodException {

        RuntimeManager runtimeManager = RuntimeManager.builder()
                .setDependencyContext(new SimpleDependencyInjectableContext())
                .setLock(null)
                .build();

        runtimeManager.executeMethod
                (new RuntimeManagerTest(),
                        RuntimeManagerTest.class.getMethod("methodWithNullable", ParameterClass.class));
    }

    public void methodWithNoNullable(ParameterClass parameterClass) {

    }

    public void methodWithNullable(@Nullable ParameterClass parameterClass) {

    }


    public static class ParameterClass {
    }

}