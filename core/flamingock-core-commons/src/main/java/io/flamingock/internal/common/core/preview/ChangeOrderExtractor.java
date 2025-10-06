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
package io.flamingock.internal.common.core.preview;

import io.flamingock.internal.common.core.error.FlamingockException;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ChangeOrderExtractor {


    // For template files: must start with _order__ (e.g., _002__whatever.yaml, _V1_2_3__whatever.yaml)
    // Recommended format: _YYYYMMDD_NN__description (e.g., _20250101_01__create_users.yaml)
    // Captures ORDER before double underscore separator
    private static final Pattern SIMPLE_FILE_ORDER_REGEX_PATTERN = Pattern.compile("^_(.+?)__(.+)$");

    // For class names: must have _order__ at the beginning of the class name after package
    // (e.g., com.mycompany.mypackage._002__MyChange or com.mycompany.OuterClass$_V1_2_3__InnerChange)
    // Captures ORDER before double underscore separator
    private static final Pattern FILE_WITH_PACKAGE_ORDER_REGEX_PATTERN = Pattern.compile("[.$]_(.+?)__(.+)$");

    private ChangeOrderExtractor() {
    }

    /**
     * For TemplateLoadedChange - validates order from template file name
     */
    public static String extractOrderFromFile(String changeId, String fileName) {
        return getOrderFromFileName(fileName, false)
                .orElseThrow(() -> getFlamingockException(changeId,  "fileName", "yaml"));
    }

    /**
     * For CodeLoadedChange - validates order from class name
     */
    public static String extractOrderFromClassName(String changeId, String classPath) {
        return getOrderFromFileName(classPath, true)
                .orElseThrow(() -> getFlamingockException(changeId, "className", "java"));
    }


    private static Optional<String> getOrderFromFileName(String fileName, boolean withPackage) {
        if (fileName == null) {
            return Optional.empty();
        }
        Pattern pattern = withPackage ? FILE_WITH_PACKAGE_ORDER_REGEX_PATTERN : SIMPLE_FILE_ORDER_REGEX_PATTERN;

        Matcher matcher = pattern.matcher(fileName);

        if (matcher.find()) {
            return Optional.ofNullable(matcher.group(1));
        }

        return Optional.empty();
    }


    private static FlamingockException getFlamingockException(String changeId, String fileType, String fileExt) {
        return new FlamingockException(String.format("Change[%s] : order must be present in the %s(e.g. _0001__%s.%s)",
                changeId, fileType, changeId, fileExt));
    }

}
