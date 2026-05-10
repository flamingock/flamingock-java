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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

/**
 * Build-time SPI walker for the GraalVM {@link RegistrationFeature}. Iterates every
 * {@code FlamingockMetadataProvider} registered on the classpath and packages each
 * module's GraalVM-relevant data into a {@link MetadataModuleInfo} record so the feature
 * can do its registration passes off a single collection.
 *
 * <p>Like {@link RegistrationFeature}, this class deliberately keeps zero compile-time
 * dependencies on Flamingock's runtime classes. The {@code FlamingockMetadataProvider}
 * SPI interface is referenced by fully-qualified name and loaded via
 * {@link Class#forName(String, boolean, ClassLoader)} with {@code initialize=false}.
 * Provider-instance method calls ({@code getMetadataResourcePath},
 * {@code getReflectClassesResourcePath}) go through {@link Method#invoke}.
 */
final class MetadataModuleInfoLoader {

    private static final String PROVIDER_INTERFACE_FQN =
            "io.flamingock.internal.common.core.metadata.FlamingockMetadataProvider";

    /**
     * Per-module GraalVM data assembled at native-image build time. Carries the module
     * reference (needed by {@link org.graalvm.nativeimage.hosted.RuntimeResourceAccess}),
     * the path to the metadata JSON resource that has to be registered for runtime access,
     * and the list of fully-qualified class names the feature will register for reflection.
     */
    record MetadataModuleInfo(Module module,
                              String metadataResourcePath,
                              List<String> reflectionClasses) {
    }

    /**
     * Walk the {@code FlamingockMetadataProvider} SPI and collect one {@link MetadataModuleInfo}
     * per registered module. Fails fast when no provider is on the classpath — same error
     * the runtime {@code MetadataLoader} would throw, surfaced earlier so a misconfigured
     * native-image build doesn't silently produce a binary with an empty pipeline.
     */
    static List<MetadataModuleInfo> load() {
        Class<?> providerInterface;
        Method getMetadataResourcePath;
        Method getReflectClassesResourcePath;
        try {
            providerInterface = Class.forName(PROVIDER_INTERFACE_FQN, false,
                    RegistrationFeature.class.getClassLoader());
            getMetadataResourcePath = providerInterface.getMethod("getMetadataResourcePath");
            getReflectClassesResourcePath = providerInterface.getMethod("getReflectClassesResourcePath");
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            throw new RuntimeException(
                    "Flamingock SPI interface not found on the classpath: "
                            + PROVIDER_INTERFACE_FQN, e);
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        ServiceLoader<?> loader = ServiceLoader.load((Class) providerInterface,
                RegistrationFeature.class.getClassLoader());

        List<MetadataModuleInfo> result = new ArrayList<>();
        for (Object provider : loader) {
            try {
                String metadataPath = (String) getMetadataResourcePath.invoke(provider);
                String reflectPath = (String) getReflectClassesResourcePath.invoke(provider);
                result.add(new MetadataModuleInfo(
                        provider.getClass().getModule(),
                        metadataPath,
                        FileUtil.fromFile(reflectPath)));
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException("Failed to read metadata resource path from provider "
                        + provider.getClass().getName(), e);
            }
        }
        if (result.isEmpty()) {
            throw new RuntimeException(
                    "Flamingock: no FlamingockMetadataProvider found on the classpath. "
                            + "Add the flamingock-processor as an annotation processor to a "
                            + "Flamingock-aware module.");
        }
        return result;
    }

    private MetadataModuleInfoLoader() {
    }
}
