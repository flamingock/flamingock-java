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
package io.flamingock.graalvm;

import io.flamingock.internal.common.core.metadata.FlamingockMetadataProvider;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ServiceLoader;
import java.util.stream.Collectors;

public final class FileUtil {

    private FileUtil() {
    }

    /**
     * Aggregate every Flamingock-aware module's reflection-classes file into a single list.
     * Each module advertises its own per-module file path via the
     * {@link FlamingockMetadataProvider} SPI; missing files are tolerated (a module without
     * code-changes may legitimately produce no reflection classes), but if NO provider is
     * registered we fail fast with the same error the runtime would have thrown.
     */
    static List<String> getClassesForRegistration() {
        List<String> all = new ArrayList<>();
        boolean providerSeen = false;
        for (FlamingockMetadataProvider provider :
                ServiceLoader.load(FlamingockMetadataProvider.class,
                        RegistrationFeature.class.getClassLoader())) {
            providerSeen = true;
            all.addAll(fromFile(provider.getReflectClassesResourcePath()));
        }
        if (!providerSeen) {
            throw new RuntimeException(
                    "Flamingock: no FlamingockMetadataProvider found on the classpath. "
                            + "Add the flamingock-processor as an annotation processor to a "
                            + "Flamingock-aware module.");
        }
        return all;
    }

    private static List<String> fromFile(String filePath) {
        ClassLoader classLoader = RegistrationFeature.class.getClassLoader();
        try (InputStream inputStream = classLoader.getResourceAsStream(filePath)) {
            if (inputStream == null) {
                return Collections.emptyList();
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.toList());
            }
        } catch (IOException e) {
            throw new RuntimeException(String.format("Error reading file `%s`", filePath), e);
        }
    }
}
