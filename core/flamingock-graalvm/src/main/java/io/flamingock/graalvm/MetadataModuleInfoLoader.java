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

import io.flamingock.internal.common.core.metadata.FlamingockMetadataProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

/**
 * Build-time SPI walker for the GraalVM {@link RegistrationFeature}. Iterates every
 * {@link FlamingockMetadataProvider} registered on the classpath and packages each module's
 * GraalVM-relevant data into a {@link MetadataModuleInfo} record so the feature can do its
 * registration passes off a single collection.
 *
 * <p>Single source of truth for the per-module SPI walk; replaces the older two-pass
 * approach where the SPI was iterated separately to read reflection classes and to register
 * metadata resources. One walk, one fail-fast, and a per-provider record that's easy to
 * extend if more registration data becomes per-module in the future.
 */
final class MetadataModuleInfoLoader {

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
     * Walk the {@link FlamingockMetadataProvider} SPI and collect one {@link MetadataModuleInfo}
     * per registered module. Fails fast when no provider is on the classpath — same error
     * the runtime {@code MetadataLoader} would throw, surfaced earlier so a misconfigured
     * native-image build doesn't silently produce a binary with an empty pipeline.
     */
    static List<MetadataModuleInfo> load() {
        List<MetadataModuleInfo> result = new ArrayList<>();
        for (FlamingockMetadataProvider provider :
                ServiceLoader.load(FlamingockMetadataProvider.class,
                        RegistrationFeature.class.getClassLoader())) {
            result.add(new MetadataModuleInfo(
                    provider.getClass().getModule(),
                    provider.getMetadataResourcePath(),
                    FileUtil.fromFile(provider.getReflectClassesResourcePath())));
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
