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
package io.flamingock.template.mongodb.mapper;

import com.mongodb.client.model.Collation;
import com.mongodb.client.model.CollationStrength;
import com.mongodb.client.model.UpdateOptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class UpdateOptionsMapperTest {

    @Nested
    @DisplayName("mapToUpdateOptions Tests")
    class MapToUpdateOptionsTests {

        @Test
        @DisplayName("WHEN options is empty THEN returns default UpdateOptions")
        void emptyOptionsTest() {
            Map<String, Object> options = new HashMap<>();

            UpdateOptions result = UpdateOptionsMapper.mapToUpdateOptions(options);

            assertNotNull(result);
            assertFalse(result.isUpsert());
        }

        @Test
        @DisplayName("WHEN upsert is true THEN option is set")
        void upsertTrueTest() {
            Map<String, Object> options = new HashMap<>();
            options.put("upsert", true);

            UpdateOptions result = UpdateOptionsMapper.mapToUpdateOptions(options);

            assertNotNull(result);
            assertTrue(result.isUpsert());
        }

        @Test
        @DisplayName("WHEN upsert is false THEN option is set")
        void upsertFalseTest() {
            Map<String, Object> options = new HashMap<>();
            options.put("upsert", false);

            UpdateOptions result = UpdateOptionsMapper.mapToUpdateOptions(options);

            assertNotNull(result);
            assertFalse(result.isUpsert());
        }

        @Test
        @DisplayName("WHEN bypassDocumentValidation is true THEN option is set")
        void bypassDocumentValidationTrueTest() {
            Map<String, Object> options = new HashMap<>();
            options.put("bypassDocumentValidation", true);

            UpdateOptions result = UpdateOptionsMapper.mapToUpdateOptions(options);

            assertNotNull(result);
            assertEquals(Boolean.TRUE, result.getBypassDocumentValidation());
        }

        @Test
        @DisplayName("WHEN collation is set THEN option is applied")
        void collationTest() {
            Collation collation = Collation.builder()
                    .locale("en")
                    .collationStrength(CollationStrength.SECONDARY)
                    .build();

            Map<String, Object> options = new HashMap<>();
            options.put("collation", collation);

            UpdateOptions result = UpdateOptionsMapper.mapToUpdateOptions(options);

            assertNotNull(result);
            assertNotNull(result.getCollation());
            assertEquals("en", result.getCollation().getLocale());
        }

        @Test
        @DisplayName("WHEN arrayFilters is set with maps THEN option is applied")
        void arrayFiltersWithMapsTest() {
            List<Map<String, Object>> arrayFilters = new ArrayList<>();
            Map<String, Object> filter1 = new HashMap<>();
            filter1.put("elem.grade", "A");
            arrayFilters.add(filter1);

            Map<String, Object> options = new HashMap<>();
            options.put("arrayFilters", arrayFilters);

            UpdateOptions result = UpdateOptionsMapper.mapToUpdateOptions(options);

            assertNotNull(result);
            assertNotNull(result.getArrayFilters());
            assertEquals(1, result.getArrayFilters().size());
        }

        @Test
        @DisplayName("WHEN multiple options are set THEN all are applied")
        void multipleOptionsTest() {
            Map<String, Object> options = new HashMap<>();
            options.put("upsert", true);
            options.put("bypassDocumentValidation", true);

            UpdateOptions result = UpdateOptionsMapper.mapToUpdateOptions(options);

            assertNotNull(result);
            assertTrue(result.isUpsert());
            assertEquals(Boolean.TRUE, result.getBypassDocumentValidation());
        }

        @Test
        @DisplayName("WHEN upsert is wrong type THEN throws exception")
        void upsertWrongTypeTest() {
            Map<String, Object> options = new HashMap<>();
            options.put("upsert", "not a boolean");

            assertThrows(IllegalArgumentException.class, () ->
                    UpdateOptionsMapper.mapToUpdateOptions(options));
        }
    }
}
