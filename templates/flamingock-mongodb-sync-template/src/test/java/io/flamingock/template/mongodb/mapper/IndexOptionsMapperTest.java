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
import com.mongodb.client.model.IndexOptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class IndexOptionsMapperTest {

    @Test
    @DisplayName("WHEN options is empty THEN returns default IndexOptions")
    void emptyOptionsTest() {
        Map<String, Object> options = new HashMap<>();

        IndexOptions result = IndexOptionsMapper.mapToIndexOptions(options);

        assertNotNull(result);
    }

    @Nested
    @DisplayName("Boolean Options Tests")
    class BooleanOptionsTests {

        @Test
        @DisplayName("WHEN background is true THEN option is set")
        void backgroundTrueTest() {
            Map<String, Object> options = new HashMap<>();
            options.put("background", true);

            IndexOptions result = IndexOptionsMapper.mapToIndexOptions(options);

            assertTrue(result.isBackground());
        }

        @Test
        @DisplayName("WHEN unique is true THEN option is set")
        void uniqueTrueTest() {
            Map<String, Object> options = new HashMap<>();
            options.put("unique", true);

            IndexOptions result = IndexOptionsMapper.mapToIndexOptions(options);

            assertTrue(result.isUnique());
        }

        @Test
        @DisplayName("WHEN sparse is true THEN option is set")
        void sparseTrueTest() {
            Map<String, Object> options = new HashMap<>();
            options.put("sparse", true);

            IndexOptions result = IndexOptionsMapper.mapToIndexOptions(options);

            assertTrue(result.isSparse());
        }
    }

    @Nested
    @DisplayName("String Options Tests")
    class StringOptionsTests {

        @Test
        @DisplayName("WHEN name is set THEN option is set")
        void nameTest() {
            Map<String, Object> options = new HashMap<>();
            options.put("name", "my_custom_index");

            IndexOptions result = IndexOptionsMapper.mapToIndexOptions(options);

            assertEquals("my_custom_index", result.getName());
        }

        @Test
        @DisplayName("WHEN defaultLanguage is set THEN option is set")
        void defaultLanguageTest() {
            Map<String, Object> options = new HashMap<>();
            options.put("defaultLanguage", "spanish");

            IndexOptions result = IndexOptionsMapper.mapToIndexOptions(options);

            assertEquals("spanish", result.getDefaultLanguage());
        }

        @Test
        @DisplayName("WHEN languageOverride is set THEN option is set")
        void languageOverrideTest() {
            Map<String, Object> options = new HashMap<>();
            options.put("languageOverride", "idioma");

            IndexOptions result = IndexOptionsMapper.mapToIndexOptions(options);

            assertEquals("idioma", result.getLanguageOverride());
        }
    }

    @Nested
    @DisplayName("Numeric Options Tests")
    class NumericOptionsTests {

        @Test
        @DisplayName("WHEN expireAfterSeconds is set THEN option is set")
        void expireAfterSecondsTest() {
            Map<String, Object> options = new HashMap<>();
            options.put("expireAfterSeconds", 3600L);

            IndexOptions result = IndexOptionsMapper.mapToIndexOptions(options);

            assertEquals(3600L, result.getExpireAfter(TimeUnit.SECONDS));
        }

        @Test
        @DisplayName("WHEN version is set THEN option is set")
        void versionTest() {
            Map<String, Object> options = new HashMap<>();
            options.put("version", 2);

            IndexOptions result = IndexOptionsMapper.mapToIndexOptions(options);

            assertEquals(2, result.getVersion());
        }

        @Test
        @DisplayName("WHEN textVersion is set THEN option is set")
        void textVersionTest() {
            Map<String, Object> options = new HashMap<>();
            options.put("textVersion", 3);

            IndexOptions result = IndexOptionsMapper.mapToIndexOptions(options);

            assertEquals(3, result.getTextVersion());
        }

        @Test
        @DisplayName("WHEN sphereVersion is set THEN option is set")
        void sphereVersionTest() {
            Map<String, Object> options = new HashMap<>();
            options.put("sphereVersion", 2);

            IndexOptions result = IndexOptionsMapper.mapToIndexOptions(options);

            assertEquals(2, result.getSphereVersion());
        }

        @Test
        @DisplayName("WHEN bits is set THEN option is set")
        void bitsTest() {
            Map<String, Object> options = new HashMap<>();
            options.put("bits", 26);

            IndexOptions result = IndexOptionsMapper.mapToIndexOptions(options);

            assertEquals(26, result.getBits());
        }

        @Test
        @DisplayName("WHEN min is set THEN option is set")
        void minTest() {
            Map<String, Object> options = new HashMap<>();
            options.put("min", -180.0);

            IndexOptions result = IndexOptionsMapper.mapToIndexOptions(options);

            assertEquals(-180.0, result.getMin(), 0.001);
        }

        @Test
        @DisplayName("WHEN max is set THEN option is set")
        void maxTest() {
            Map<String, Object> options = new HashMap<>();
            options.put("max", 180.0);

            IndexOptions result = IndexOptionsMapper.mapToIndexOptions(options);

            assertEquals(180.0, result.getMax(), 0.001);
        }
    }

    @Nested
    @DisplayName("Bson Options Tests")
    class BsonOptionsTests {

        @Test
        @DisplayName("WHEN weights is set THEN option is set")
        void weightsTest() {
            Map<String, Object> weights = new HashMap<>();
            weights.put("title", 10);
            weights.put("content", 5);

            Map<String, Object> options = new HashMap<>();
            options.put("weights", weights);

            IndexOptions result = IndexOptionsMapper.mapToIndexOptions(options);

            assertNotNull(result.getWeights());
        }

        @Test
        @DisplayName("WHEN storageEngine is set THEN option is set")
        void storageEngineTest() {
            Map<String, Object> storageEngine = new HashMap<>();
            storageEngine.put("wiredTiger", new HashMap<>());

            Map<String, Object> options = new HashMap<>();
            options.put("storageEngine", storageEngine);

            IndexOptions result = IndexOptionsMapper.mapToIndexOptions(options);

            assertNotNull(result.getStorageEngine());
        }

        @Test
        @DisplayName("WHEN partialFilterExpression is set THEN option is set")
        void partialFilterExpressionTest() {
            Map<String, Object> filter = new HashMap<>();
            filter.put("status", "active");

            Map<String, Object> options = new HashMap<>();
            options.put("partialFilterExpression", filter);

            IndexOptions result = IndexOptionsMapper.mapToIndexOptions(options);

            assertNotNull(result.getPartialFilterExpression());
        }
    }

    @Nested
    @DisplayName("Collation Tests")
    class CollationTests {

        @Test
        @DisplayName("WHEN collation is set THEN option is set")
        void collationTest() {
            Collation collation = Collation.builder()
                    .locale("en")
                    .collationStrength(CollationStrength.SECONDARY)
                    .build();

            Map<String, Object> options = new HashMap<>();
            options.put("collation", collation);

            IndexOptions result = IndexOptionsMapper.mapToIndexOptions(options);

            assertNotNull(result.getCollation());
            assertEquals("en", result.getCollation().getLocale());
        }
    }

    @Nested
    @DisplayName("Unsupported Options Tests")
    class UnsupportedOptionsTests {

        @Test
        @DisplayName("WHEN bucketSize is set THEN throws UnsupportedOperationException")
        void bucketSizeTest() {
            Map<String, Object> options = new HashMap<>();
            options.put("bucketSize", 1.0);

            assertThrows(UnsupportedOperationException.class, () ->
                    IndexOptionsMapper.mapToIndexOptions(options));
        }

        @Test
        @DisplayName("WHEN wildcardProjection is set THEN throws UnsupportedOperationException")
        void wildcardProjectionTest() {
            Map<String, Object> options = new HashMap<>();
            options.put("wildcardProjection", new HashMap<>());

            assertThrows(UnsupportedOperationException.class, () ->
                    IndexOptionsMapper.mapToIndexOptions(options));
        }

        @Test
        @DisplayName("WHEN hidden is set THEN throws UnsupportedOperationException")
        void hiddenTest() {
            Map<String, Object> options = new HashMap<>();
            options.put("hidden", true);

            assertThrows(UnsupportedOperationException.class, () ->
                    IndexOptionsMapper.mapToIndexOptions(options));
        }
    }

    @Test
    @DisplayName("WHEN multiple options are set THEN all are applied")
    void multipleOptionsTest() {
        Map<String, Object> options = new HashMap<>();
        options.put("unique", true);
        options.put("sparse", true);
        options.put("name", "compound_index");
        options.put("background", false);

        IndexOptions result = IndexOptionsMapper.mapToIndexOptions(options);

        assertTrue(result.isUnique());
        assertTrue(result.isSparse());
        assertEquals("compound_index", result.getName());
        assertFalse(result.isBackground());
    }
}
