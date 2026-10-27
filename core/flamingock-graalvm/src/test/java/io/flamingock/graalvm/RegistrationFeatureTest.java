/*
 * Copyright 2026 Flamingock (https://www.flamingock.io)
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
package io.flamingock.graalvm;

import org.graalvm.nativeimage.hosted.RuntimeReflection;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mockStatic;

class RegistrationFeatureTest {

    private static boolean initialized;

    @Test
    void shouldRegisterUserHierarchyWithoutInitialization() throws Exception {
        try (MockedStatic<RuntimeReflection> reflection = mockStatic(RuntimeReflection.class)) {
            Method register = RegistrationFeature.class.getDeclaredMethod("registerUserClassForReflection", String.class);
            register.setAccessible(true);
            register.invoke(null, ConcreteChange.class.getName());

            for (Class<?> type : new Class<?>[]{ConcreteChange.class, MiddleChange.class, BaseChange.class}) {
                reflection.verify(() -> RuntimeReflection.register(type));
                reflection.verify(() -> RuntimeReflection.register(type.getDeclaredMethods()));
            }
            reflection.verify(() -> RuntimeReflection.register(Object.class), org.mockito.Mockito.never());
            assertFalse(initialized);
        }
    }

    static class BaseChange {
        public void apply() { }
        public void rollback() { }
    }

    static class MiddleChange extends BaseChange {
        public void middle() { }
    }

    static class ConcreteChange extends MiddleChange {
        static {
            initialized = true;
        }

        public void concrete() { }
    }
}
