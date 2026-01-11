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

import com.mongodb.client.model.RenameCollectionOptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RenameCollectionOptionsMapperTest {

    @Test
    @DisplayName("WHEN options is empty THEN returns default RenameCollectionOptions")
    void emptyOptionsTest() {
        Map<String, Object> options = new HashMap<>();

        RenameCollectionOptions result = RenameCollectionOptionsMapper.map(options);

        assertNotNull(result);
    }

    @Test
    @DisplayName("WHEN dropTarget is true THEN option is set")
    void dropTargetTrueTest() {
        Map<String, Object> options = new HashMap<>();
        options.put("dropTarget", true);

        RenameCollectionOptions result = RenameCollectionOptionsMapper.map(options);

        assertTrue(result.isDropTarget());
    }

    @Test
    @DisplayName("WHEN dropTarget is false THEN option is set")
    void dropTargetFalseTest() {
        Map<String, Object> options = new HashMap<>();
        options.put("dropTarget", false);

        RenameCollectionOptions result = RenameCollectionOptionsMapper.map(options);

        assertFalse(result.isDropTarget());
    }

    @Test
    @DisplayName("WHEN dropTarget is wrong type THEN throws exception")
    void dropTargetWrongTypeTest() {
        Map<String, Object> options = new HashMap<>();
        options.put("dropTarget", "not a boolean");

        assertThrows(IllegalArgumentException.class, () ->
                RenameCollectionOptionsMapper.map(options));
    }
}
