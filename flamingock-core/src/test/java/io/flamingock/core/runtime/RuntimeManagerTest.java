package io.flamingock.core.runtime;


import io.flamingock.core.api.annotations.Nullable;
import io.flamingock.core.context.SimpleContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RuntimeManagerTest {

    @Test
    @DisplayName("should throw exception when executing method if no dependency and not annotated with @Nullable")
    void shouldThrowExceptionIfNoNullableAnnotation() throws NoSuchMethodException {

        RuntimeManager runtimeManager = RuntimeManager.builder()
                .setDependencyContext(new SimpleContext())
                .setLock(null)
                .build();

        Method methodWithNoNullable = RuntimeManagerTest.class.getMethod("methodWithNoNullable", ParameterClass.class);
        MissingInjectedParameterException ex = Assertions.assertThrows(MissingInjectedParameterException.class, () -> runtimeManager.executeMethodWithInjectedDependencies(new RuntimeManagerTest(), methodWithNoNullable));
        assertEquals(ParameterClass.class, ex.getWrongParameter());
    }

    @Test
    @DisplayName("should not throw exception when executing method if no dependency and parameter is annotated with @Nullable")
    void shouldNotThrowExceptionIfNullableAnnotation() throws NoSuchMethodException {

        RuntimeManager runtimeManager = RuntimeManager.builder()
                .setDependencyContext(new SimpleContext())
                .setLock(null)
                .build();

        runtimeManager.executeMethodWithInjectedDependencies
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