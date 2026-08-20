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
package io.flamingock.internal.core.context;

import io.flamingock.internal.common.core.context.Dependency;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.net.URL;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reproduces https://github.com/flamingock/flamingock-java/issues/951
 * <p>
 * Simulates what Spring Boot DevTools' RestartClassLoader does: the same
 * fully-qualified class is loaded twice by two different classloaders,
 * producing two distinct {@code Class<?>} instances with the same name.
 * SimpleContext keys its dependency map by exact {@code Class<?>} identity,
 * so a dependency registered under one loader's Class instance is not found
 * when looked up with the other loader's Class instance.
 */
class ClassloaderMismatchReproTest {

    private static final String TARGET_CLASS = "io.flamingock.internal.core.context.repro.MigrationConfiguration";

    @Test
    void dependencyRegisteredUnderOneClassloaderIsNotFoundUnderAnother() throws Exception {
        ClassLoader appLoader = ClassloaderMismatchReproTest.class.getClassLoader();
        Class<?> typeFromRegistration = loadIsolated(appLoader).loadClass(TARGET_CLASS);
        Class<?> typeFromLookup = loadIsolated(appLoader).loadClass(TARGET_CLASS);

        assertFalse(typeFromRegistration.equals(typeFromLookup),
                "precondition: the two loaders must produce distinct Class instances");

        SimpleContext context = new SimpleContext();
        Object instance = typeFromRegistration.getConstructor(String.class)
                .newInstance("some-config");
        context.addDependency(new Dependency(typeFromRegistration, instance));

        boolean found = context.getDependency(typeFromLookup).isPresent();

        assertTrue(found, "dependency should be found by name across classloader boundaries");
    }

    private static IsolatedClassLoader loadIsolated(ClassLoader parent) {
        return new IsolatedClassLoader(parent);
    }

    /** Loads only classes under the repro package in isolation; delegates everything else to parent. */
    private static class IsolatedClassLoader extends ClassLoader {
        private final ClassLoader resourceLoader;

        IsolatedClassLoader(ClassLoader resourceLoader) {
            super(null);
            this.resourceLoader = resourceLoader;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.equals(TARGET_CLASS)) {
                synchronized (getClassLoadingLock(name)) {
                    Class<?> loaded = findLoadedClass(name);
                    if (loaded == null) {
                        String path = name.replace('.', '/') + ".class";
                        try (InputStream is = resourceLoader.getResourceAsStream(path)) {
                            if (is == null) {
                                throw new ClassNotFoundException(name);
                            }
                            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                            byte[] buf = new byte[4096];
                            int n;
                            while ((n = is.read(buf)) != -1) {
                                baos.write(buf, 0, n);
                            }
                            byte[] bytes = baos.toByteArray();
                            loaded = defineClass(name, bytes, 0, bytes.length);
                        } catch (Exception e) {
                            throw new ClassNotFoundException(name, e);
                        }
                    }
                    if (resolve) {
                        resolveClass(loaded);
                    }
                    return loaded;
                }
            }
            return Class.forName(name, resolve, resourceLoader);
        }

        @Override
        public URL getResource(String name) {
            return resourceLoader.getResource(name);
        }
    }
}
