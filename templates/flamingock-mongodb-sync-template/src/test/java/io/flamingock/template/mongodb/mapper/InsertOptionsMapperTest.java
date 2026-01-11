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

import com.mongodb.client.model.InsertManyOptions;
import com.mongodb.client.model.InsertOneOptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class InsertOptionsMapperTest {

    @Nested
    @DisplayName("mapToInsertOneOptions Tests")
    class MapToInsertOneOptionsTests {

        @Test
        @DisplayName("WHEN options is empty THEN returns default InsertOneOptions")
        void emptyOptionsTest() {
            Map<String, Object> options = new HashMap<>();

            InsertOneOptions result = InsertOptionsMapper.mapToInsertOneOptions(options);

            assertNotNull(result);
        }

        @Test
        @DisplayName("WHEN bypassDocumentValidation is true THEN option is set")
        void bypassDocumentValidationTrueTest() {
            Map<String, Object> options = new HashMap<>();
            options.put("bypassDocumentValidation", true);

            InsertOneOptions result = InsertOptionsMapper.mapToInsertOneOptions(options);

            assertNotNull(result);
            assertEquals(Boolean.TRUE, result.getBypassDocumentValidation());
        }

        @Test
        @DisplayName("WHEN bypassDocumentValidation is false THEN option is set")
        void bypassDocumentValidationFalseTest() {
            Map<String, Object> options = new HashMap<>();
            options.put("bypassDocumentValidation", false);

            InsertOneOptions result = InsertOptionsMapper.mapToInsertOneOptions(options);

            assertNotNull(result);
            assertNotEquals(Boolean.TRUE, result.getBypassDocumentValidation());
        }

        @Test
        @DisplayName("WHEN bypassDocumentValidation is wrong type THEN throws exception")
        void bypassDocumentValidationWrongTypeTest() {
            Map<String, Object> options = new HashMap<>();
            options.put("bypassDocumentValidation", "not a boolean");

            assertThrows(IllegalArgumentException.class, () ->
                    InsertOptionsMapper.mapToInsertOneOptions(options));
        }
    }

    @Nested
    @DisplayName("mapToInsertManyOptions Tests")
    class MapToInsertManyOptionsTests {

        @Test
        @DisplayName("WHEN options is empty THEN returns default InsertManyOptions")
        void emptyOptionsTest() {
            Map<String, Object> options = new HashMap<>();

            InsertManyOptions result = InsertOptionsMapper.mapToInsertManyOptions(options);

            assertNotNull(result);
        }

        @Test
        @DisplayName("WHEN bypassDocumentValidation is true THEN option is set")
        void bypassDocumentValidationTrueTest() {
            Map<String, Object> options = new HashMap<>();
            options.put("bypassDocumentValidation", true);

            InsertManyOptions result = InsertOptionsMapper.mapToInsertManyOptions(options);

            assertNotNull(result);
            assertEquals(Boolean.TRUE, result.getBypassDocumentValidation());
        }

        @Test
        @DisplayName("WHEN bypassDocumentValidation is false THEN option is set")
        void bypassDocumentValidationFalseTest() {
            Map<String, Object> options = new HashMap<>();
            options.put("bypassDocumentValidation", false);

            InsertManyOptions result = InsertOptionsMapper.mapToInsertManyOptions(options);

            assertNotNull(result);
            assertNotEquals(Boolean.TRUE, result.getBypassDocumentValidation());
        }

        @Test
        @DisplayName("WHEN ordered is true THEN option is set")
        void orderedTrueTest() {
            Map<String, Object> options = new HashMap<>();
            options.put("ordered", true);

            InsertManyOptions result = InsertOptionsMapper.mapToInsertManyOptions(options);

            assertNotNull(result);
            assertTrue(result.isOrdered());
        }

        @Test
        @DisplayName("WHEN ordered is false THEN option is set")
        void orderedFalseTest() {
            Map<String, Object> options = new HashMap<>();
            options.put("ordered", false);

            InsertManyOptions result = InsertOptionsMapper.mapToInsertManyOptions(options);

            assertNotNull(result);
            assertFalse(result.isOrdered());
        }

        @Test
        @DisplayName("WHEN both options are set THEN both are applied")
        void bothOptionsTest() {
            Map<String, Object> options = new HashMap<>();
            options.put("bypassDocumentValidation", true);
            options.put("ordered", false);

            InsertManyOptions result = InsertOptionsMapper.mapToInsertManyOptions(options);

            assertNotNull(result);
            assertEquals(Boolean.TRUE, result.getBypassDocumentValidation());
            assertFalse(result.isOrdered());
        }

        @Test
        @DisplayName("WHEN ordered is wrong type THEN throws exception")
        void orderedWrongTypeTest() {
            Map<String, Object> options = new HashMap<>();
            options.put("ordered", "not a boolean");

            assertThrows(IllegalArgumentException.class, () ->
                    InsertOptionsMapper.mapToInsertManyOptions(options));
        }
    }
}
