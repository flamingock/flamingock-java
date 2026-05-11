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
package io.flamingock.internal.common.core.metadata;

/**
 * Service Provider Interface registered per Flamingock-aware module.
 *
 * <p>Each module's annotation processor generates an implementation of this interface and
 * declares it in {@code META-INF/services/io.flamingock.internal.common.core.metadata
 * .FlamingockMetadataProvider}. At runtime the {@code MetadataLoader} discovers all providers
 * via {@link java.util.ServiceLoader} and aggregates each module's metadata file into a single
 * composite {@link FlamingockMetadata} consumed by the runner.
 *
 * <p>Providers are stateless: they only advertise the resource path of the metadata file they
 * own. The path is module-unique (suffixed at compile time) so multiple modules can coexist on
 * the same classpath without colliding.
 */
public interface FlamingockMetadataProvider {

    /**
     * @return the classpath resource path where this module's serialized
     *         {@link FlamingockMetadata} JSON file lives, e.g.
     *         {@code "META-INF/flamingock/metadata_a1b2c3d4.json"}. Loaders use this with
     *         {@link ClassLoader#getResourceAsStream(String)}.
     */
    String getMetadataResourcePath();

    /**
     * @return the classpath resource path where this module's GraalVM reflection-classes
     *         file lives. Default implementation derives the path from
     *         {@link #getMetadataResourcePath()} by replacing the {@code metadata_} segment
     *         with {@code reflection-classes_} and the {@code .json} extension with
     *         {@code .txt}. Generated providers may override if their layout differs.
     */
    default String getReflectClassesResourcePath() {
        return getMetadataResourcePath()
                .replace("/metadata_", "/reflection-classes_")
                .replace(".json", ".txt");
    }
}
