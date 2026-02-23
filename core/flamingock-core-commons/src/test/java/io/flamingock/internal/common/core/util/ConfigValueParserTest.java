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
package io.flamingock.internal.common.core.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.function.Predicate;

import static io.flamingock.internal.common.core.util.ConfigValueParser.BOOLEAN_VALUE_VALIDATOR;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ConfigValueParser}.
 *
 * <p>These tests cover:</p>
 * <ul>
 *   <li>Placeholder syntactic validation</li>
 *   <li>Placeholder parsing</li>
 *   <li>Config value classification (EMPTY, LITERAL, PLACEHOLDER)</li>
 *   <li>Fail-fast behaviour for invalid placeholder syntax</li>
 *   <li>Validation of literal and placeholder default values using a single validator</li>
 * </ul>
 */
class ConfigValueParserTest {

    @Test
    @DisplayName("isValidPlaceholder: should accept valid placeholders without defaults")
    void isValidPlaceholder_shouldAcceptValidWithoutDefaults() {
        assertTrue(ConfigValueParser.isValidPlaceholder("${a}"));
        assertTrue(ConfigValueParser.isValidPlaceholder("${my.custom.property}"));
        assertTrue(ConfigValueParser.isValidPlaceholder("${my-custom-property}"));
        assertTrue(ConfigValueParser.isValidPlaceholder("${my_custom_property}"));
        assertTrue(ConfigValueParser.isValidPlaceholder("${A1._-b}"));

        assertTrue(ConfigValueParser.isValidPlaceholder("${  my.custom.property  }"));
    }

    @Test
    @DisplayName("isValidPlaceholder: should accept valid placeholders with defaults")
    void isValidPlaceholder_shouldAcceptValidWithDefaults() {
        assertTrue(ConfigValueParser.isValidPlaceholder("${a:b}"));
        assertTrue(ConfigValueParser.isValidPlaceholder("${a:}"));
        assertTrue(ConfigValueParser.isValidPlaceholder("${my.prop:someValue}"));
        assertTrue(ConfigValueParser.isValidPlaceholder("${a:b:c}"));
    }

    @Test
    @DisplayName("isValidPlaceholder: should reject invalid syntax")
    void isValidPlaceholder_shouldRejectInvalidSyntax() {
        assertFalse(ConfigValueParser.isValidPlaceholder(null));
        assertFalse(ConfigValueParser.isValidPlaceholder(""));
        assertFalse(ConfigValueParser.isValidPlaceholder("   "));
        assertFalse(ConfigValueParser.isValidPlaceholder("${}"));
        assertFalse(ConfigValueParser.isValidPlaceholder("${:x}"));
        assertFalse(ConfigValueParser.isValidPlaceholder("${x"));
        assertFalse(ConfigValueParser.isValidPlaceholder("${my custom}"));
    }

    @Test
    @DisplayName("parsePlaceholder: should parse name and default correctly")
    void parsePlaceholder_shouldParseCorrectly() {

        Optional<ConfigValueParser.Placeholder> p1 =
                ConfigValueParser.parsePlaceholder("value", "${my.property}");
        assertTrue(p1.isPresent());
        assertEquals("my.property", p1.get().getName());
        assertFalse(p1.get().hasDefault());
        assertNull(p1.get().getDefaultValue());

        Optional<ConfigValueParser.Placeholder> p2 =
                ConfigValueParser.parsePlaceholder("value", "${my.property:default}");
        assertTrue(p2.isPresent());
        assertEquals("my.property", p2.get().getName());
        assertTrue(p2.get().hasDefault());
        assertEquals("default", p2.get().getDefaultValue());

        Optional<ConfigValueParser.Placeholder> p3 =
                ConfigValueParser.parsePlaceholder("value", "${a:b:c}");
        assertTrue(p3.isPresent());
        assertEquals("a", p3.get().getName());
        assertEquals("b:c", p3.get().getDefaultValue());
    }

    @Test
    @DisplayName("parse: should classify EMPTY, LITERAL and PLACEHOLDER")
    void parse_shouldClassifyCorrectly() {

        ConfigValueParser.ConfigValue empty =
                ConfigValueParser.parse("value", "   ");
        assertTrue(empty.isEmpty());

        ConfigValueParser.ConfigValue literal =
                ConfigValueParser.parse("value", "literalValue");
        assertTrue(literal.isLiteral());
        assertEquals("literalValue", literal.getLiteral().get());

        ConfigValueParser.ConfigValue placeholder =
                ConfigValueParser.parse("value", "${my.property}");
        assertTrue(placeholder.isPlaceholder());
        assertEquals("my.property", placeholder.getPlaceholder().get().getName());
    }

    @Test
    @DisplayName("parse: should throw for invalid placeholder syntax")
    void parse_shouldThrowForInvalidPlaceholder() {
        assertThrows(IllegalArgumentException.class,
                () -> ConfigValueParser.parse("value", "${}"));

        assertThrows(IllegalArgumentException.class,
                () -> ConfigValueParser.parse("value", "${:x}"));

        assertThrows(IllegalArgumentException.class,
                () -> ConfigValueParser.parse("value", "${x"));
    }

    @Test
    @DisplayName("parse with validator: should validate literal values")
    void parse_withValidator_shouldValidateLiteral() {

        assertDoesNotThrow(() ->
                ConfigValueParser.parse("flag", "true", BOOLEAN_VALUE_VALIDATOR));

        assertDoesNotThrow(() ->
                ConfigValueParser.parse("flag", "false", BOOLEAN_VALUE_VALIDATOR));

        assertThrows(IllegalArgumentException.class,
                () -> ConfigValueParser.parse("flag", "yes", BOOLEAN_VALUE_VALIDATOR));
    }

    @Test
    @DisplayName("parse with validator: should validate placeholder default values")
    void parse_withValidator_shouldValidatePlaceholderDefault() {

        assertDoesNotThrow(() ->
                ConfigValueParser.parse("flag", "${flag:true}", BOOLEAN_VALUE_VALIDATOR));

        assertDoesNotThrow(() ->
                ConfigValueParser.parse("flag", "${flag:}", BOOLEAN_VALUE_VALIDATOR));

        assertThrows(IllegalArgumentException.class,
                () -> ConfigValueParser.parse("flag", "${flag:yes}", BOOLEAN_VALUE_VALIDATOR));
    }

    @Test
    @DisplayName("parse with null validator: should accept any literal or default")
    void parse_withNullValidator_shouldAcceptAny() {

        assertDoesNotThrow(() ->
                ConfigValueParser.parse("value", "anything", null));

        assertDoesNotThrow(() ->
                ConfigValueParser.parse("value", "${x:anything}", null));
    }
}
