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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ChangeNamingConventionTest {

    @Nested
    @DisplayName("Valid names — order extracted correctly")
    class ValidNames {

        @Test
        @DisplayName("Should extract numeric order from class simple name")
        void shouldExtractNumericOrder() {
            assertEquals("0002", ChangeNamingConvention.extractOrder("_0002__FeedClients"));
        }

        @Test
        @DisplayName("Should extract date-based order")
        void shouldExtractDateBasedOrder() {
            assertEquals("20250101_01", ChangeNamingConvention.extractOrder("_20250101_01__InitSchema"));
        }

        @Test
        @DisplayName("Should extract version-style order")
        void shouldExtractVersionStyleOrder() {
            assertEquals("V1_2_3", ChangeNamingConvention.extractOrder("_V1_2_3__LegacyMigration"));
        }

        @Test
        @DisplayName("Should extract single-digit order")
        void shouldExtractSingleDigitOrder() {
            assertEquals("1", ChangeNamingConvention.extractOrder("_1__MyChange"));
        }

        @Test
        @DisplayName("Should work identically for file names without extension")
        void shouldWorkForFileNames() {
            assertEquals("0003", ChangeNamingConvention.extractOrder("_0003__create_users"));
        }
    }

    @Nested
    @DisplayName("Invalid names — null returned")
    class InvalidNames {

        @Test
        @DisplayName("Should return null when name is null")
        void shouldReturnNullForNull() {
            assertNull(ChangeNamingConvention.extractOrder(null));
        }

        @Test
        @DisplayName("Should return null when name does not start with underscore")
        void shouldReturnNullWhenNoLeadingUnderscore() {
            assertNull(ChangeNamingConvention.extractOrder("FeedClients"));
        }

        @Test
        @DisplayName("Should return null when name starts with underscore but has no double-underscore separator")
        void shouldReturnNullWhenNoDoubleSeparator() {
            assertNull(ChangeNamingConvention.extractOrder("_FeedClients"));
        }

        @Test
        @DisplayName("Should return null when double-underscore is at the very start")
        void shouldReturnNullWhenSeparatorAtStart() {
            assertNull(ChangeNamingConvention.extractOrder("__FeedClients"));
        }

        @Test
        @DisplayName("Should return null for empty string")
        void shouldReturnNullForEmptyString() {
            assertNull(ChangeNamingConvention.extractOrder(""));
        }
    }
}
