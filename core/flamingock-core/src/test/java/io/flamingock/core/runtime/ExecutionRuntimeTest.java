/*
 * Copyright 2025 Flamingock (https://www.flamingock.io)
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


import io.flamingock.api.annotations.Nullable;
import io.flamingock.internal.core.context.SimpleContext;
import io.flamingock.internal.core.runtime.MissingInjectedParameterException;
import io.flamingock.internal.core.runtime.ExecutionRuntime;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ExecutionRuntimeTest {

    @Test
    @DisplayName("should throw exception when executing method if no dependency and not annotated with @Nullable")
    void shouldThrowExceptionIfNoNullableAnnotation() throws NoSuchMethodException {

        ExecutionRuntime executionRuntime = ExecutionRuntime.builder()
                .setDependencyContext(new SimpleContext())
                .setLock(null)
                .build();

        Method methodWithNoNullable = ExecutionRuntimeTest.class.getMethod("methodWithNoNullable", ParameterClass.class);
        MissingInjectedParameterException ex = Assertions.assertThrows(MissingInjectedParameterException.class, () -> executionRuntime.executeMethodWithInjectedDependencies(new ExecutionRuntimeTest(), methodWithNoNullable));
        assertEquals(ParameterClass.class, ex.getWrongParameter());
    }

    @Test
    @DisplayName("should not throw exception when executing method if no dependency and parameter is annotated with @Nullable")
    void shouldNotThrowExceptionIfNullableAnnotation() throws NoSuchMethodException {

        ExecutionRuntime executionRuntime = ExecutionRuntime.builder()
                .setDependencyContext(new SimpleContext())
                .setLock(null)
                .build();

        executionRuntime.executeMethodWithInjectedDependencies
                (new ExecutionRuntimeTest(),
                        ExecutionRuntimeTest.class.getMethod("methodWithNullable", ParameterClass.class));
    }

    public void methodWithNoNullable(ParameterClass parameterClass) {

    }

    public void methodWithNullable(@Nullable ParameterClass parameterClass) {

    }


    public static class ParameterClass {
    }

}