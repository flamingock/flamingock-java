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
package io.flamingock.support.change;

/**
 * Shared utility for parsing the Flamingock naming convention that encodes execution order.
 *
 * <p>Both code-based changes (class names) and template-based changes (file names) follow
 * the same pattern:</p>
 * <pre>
 *   _ORDER__DescriptiveName
 * </pre>
 * <p>Examples:</p>
 * <ul>
 *   <li>{@code _0002__FeedClients}          → order {@code "0002"}</li>
 *   <li>{@code _20250101_01__InitSchema}    → order {@code "20250101_01"}</li>
 *   <li>{@code _V1_2_3__LegacyMigration}   → order {@code "V1_2_3"}</li>
 * </ul>
 *
 * <p>This class is package-private and intended for use by validators in this package only.</p>
 */
final class ChangeNamingConvention {

    private static final String ORDER_PREFIX = "_";
    private static final String ORDER_SEPARATOR = "__";

    private ChangeNamingConvention() {
    }

    /**
     * Extracts the order segment from a name (class simple name or file name without extension)
     * following the {@code _ORDER__DescriptiveName} convention.
     *
     * @param name the name to parse (class simple name or file name without extension)
     * @return the extracted order string, or {@code null} if the name does not follow the convention
     */
    static String extractOrder(String name) {
        if (name == null || !name.startsWith(ORDER_PREFIX)) {
            return null;
        }
        int separatorIndex = name.indexOf(ORDER_SEPARATOR);
        if (separatorIndex <= 1) {
            return null;
        }
        return name.substring(1, separatorIndex);
    }
}
