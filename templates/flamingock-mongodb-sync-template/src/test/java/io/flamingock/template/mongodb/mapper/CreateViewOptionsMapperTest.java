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
import com.mongodb.client.model.CreateViewOptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CreateViewOptionsMapperTest {

    @Test
    @DisplayName("WHEN options is empty THEN returns default CreateViewOptions")
    void emptyOptionsTest() {
        Map<String, Object> options = new HashMap<>();

        CreateViewOptions result = CreateViewOptionsMapper.map(options);

        assertNotNull(result);
    }

    @Test
    @DisplayName("WHEN collation is set THEN option is set")
    void collationTest() {
        Collation collation = Collation.builder()
                .locale("en")
                .collationStrength(CollationStrength.PRIMARY)
                .build();

        Map<String, Object> options = new HashMap<>();
        options.put("collation", collation);

        CreateViewOptions result = CreateViewOptionsMapper.map(options);

        assertNotNull(result.getCollation());
        assertEquals("en", result.getCollation().getLocale());
    }

    @Test
    @DisplayName("WHEN collation is wrong type THEN throws exception")
    void collationWrongTypeTest() {
        Map<String, Object> options = new HashMap<>();
        options.put("collation", "not a collation");

        assertThrows(IllegalArgumentException.class, () ->
                CreateViewOptionsMapper.map(options));
    }
}
