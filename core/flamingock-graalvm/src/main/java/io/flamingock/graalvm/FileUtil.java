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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static io.flamingock.internal.common.core.metadata.Constants.FULL_GRAALVM_REFLECT_CLASSES_PATH;

public final class FileUtil {

    private FileUtil(){}

    static List<String> getClassesForRegistration() {
        List<String> classesToRegister = fromFile(FULL_GRAALVM_REFLECT_CLASSES_PATH);
        if (!classesToRegister.isEmpty()) {
            return classesToRegister;
        }
        throw new RuntimeException("Flamingock: No valid reflection file found");
    }

    private static List<String> fromFile(String filePath) {
        ClassLoader classLoader = RegistrationFeature.class.getClassLoader();

        try (InputStream inputStream = classLoader.getResourceAsStream(filePath)) {
            if (inputStream == null) {
                return Collections.emptyList();
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.toList());
            }

        } catch (IOException e) {
            throw new RuntimeException(String.format("Error reading file `%s`", filePath), e);
        }
    }
}
