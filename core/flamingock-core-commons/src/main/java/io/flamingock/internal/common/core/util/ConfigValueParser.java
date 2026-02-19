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

import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Generic configuration value parser for Flamingock configuration values.
 *
 * <p>Supported placeholder syntax:</p>
 * <ul>
 *   <li>{@code ${name}}</li>
 *   <li>{@code ${name:defaultValue}}</li>
 * </ul>
 *
 * <p>Notes:</p>
 * <ul>
 *   <li>The placeholder {@code name} may include separators such as {@code .}, {@code -} and {@code _}.</li>
 *   <li>The first {@code :} (if present) separates the name from the default value.</li>
 *   <li>Whitespace around the name is allowed and ignored.</li>
 *   <li>Nested placeholders (e.g. {@code ${a:${b}}}) are intentionally not supported.</li>
 *   <li>If a value starts with ${ but is not a valid placeholder, this class throws an exception.
 *       It must not be treated as a literal.</li>
 * </ul>
 */
public final class ConfigValueParser {

    private ConfigValueParser() {
        // Utility class
    }

    private static final Pattern PLACEHOLDER_PATTERN =
            Pattern.compile("^\\$\\{\\s*([^}:\\s]+)\\s*(?::([^}]*))?}$");

    /**
     * Validator suitable for boolean-like values:
     * allowed values: "", "true", "false" (case-insensitive).
     */
    public static final Predicate<String> BOOLEAN_VALUE_VALIDATOR =
            value -> value == null
                    || value.trim().isEmpty()
                    || "true".equalsIgnoreCase(value.trim())
                    || "false".equalsIgnoreCase(value.trim());

    /**
     * Parses the provided value and classifies it as EMPTY, PLACEHOLDER, or LITERAL.
     *
     * <p>This overload performs only placeholder syntax validation. Literals and placeholder defaults
     * are accepted as-is.</p>
     *
     * @param valueName logical name used in exception messages (e.g. "origin", "emptyOriginAllowed")
     * @param raw raw input value
     * @return parsed configuration value (never invalid; invalid inputs throw)
     */
    public static ConfigValue parse(String valueName, String raw) {
        return parse(valueName, raw, null);
    }

    /**
     * Parses the provided value and classifies it as EMPTY, PLACEHOLDER, or LITERAL, while also validating:
     * <ul>
     *   <li>Placeholder syntax (always, when value starts with ${)</li>
     *   <li>Placeholder default value (only when present, using {@code valueValidator} if provided)</li>
     *   <li>Literal value (using {@code valueValidator} if provided)</li>
     * </ul>
     *
     * <p>Fail-fast rule: if the value starts with ${} but is not a valid placeholder,
     * this method throws {@link IllegalArgumentException}.</p>
     *
     * @param valueName logical name used in exception messages (e.g. "origin", "emptyOriginAllowed")
     * @param raw raw input value
     * @param valueValidator validator applied to literal values and placeholder default values (may be null)
     * @return parsed configuration value (never invalid; invalid inputs throw)
     */
    public static ConfigValue parse(String valueName, String raw, Predicate<String> valueValidator) {
        String value = normalise(raw);

        if (value.isEmpty()) {
            return ConfigValue.empty(raw);
        }

        if (value.startsWith("${")) {
            Placeholder placeholder = parsePlaceholderOrThrow(valueName, raw, value);

            if (placeholder.hasDefault()) {
                validateValue(valueName, raw, placeholder.getDefaultValue(), valueValidator);
            }

            return ConfigValue.placeholder(raw, placeholder);
        }

        // Literal
        validateValue(valueName, raw, value, valueValidator);
        return ConfigValue.literal(raw, value);
    }

    /**
     * Parses a placeholder if (and only if) the entire value is a placeholder.
     *
     * <p>Returns {@link Optional#empty()} if the input is {@code null}, blank, or not a placeholder.</p>
     *
     * <p>Important: if the value starts with ${} but is not a valid placeholder,
     * this method throws {@link IllegalArgumentException}.</p>
     */
    public static Optional<Placeholder> parsePlaceholder(String valueName, String raw) {
        ConfigValue parsed = parse(valueName, raw, null);
        return parsed.getPlaceholder();
    }

    /**
     * Returns {@code true} if the supplied value is syntactically a valid placeholder.
     *
     * <p>This method never throws. If you need fail-fast behaviour for values starting with ${,
     * use {@link #parse(String, String)}.</p>
     */
    public static boolean isValidPlaceholder(String raw) {
        String value = normalise(raw);
        if (value.isEmpty()) {
            return false;
        }
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(value);
        if (!matcher.matches()) {
            return false;
        }
        String name = matcher.group(1);
        return name != null && !name.trim().isEmpty();
    }

    private static Placeholder parsePlaceholderOrThrow(String valueName, String raw, String normalised) {
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(normalised);
        if (!matcher.matches()) {
            throw new IllegalArgumentException(buildPlaceholderSyntaxError(valueName, raw));
        }

        String name = matcher.group(1);
        String defaultValue = matcher.group(2); // null if ':' not present; may be "" if ${name:}

        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException(buildPlaceholderSyntaxError(valueName, raw));
        }

        return new Placeholder(name.trim(), defaultValue);
    }

    private static void validateValue(String valueName,
                                      String raw,
                                      String value,
                                      Predicate<String> validator) {
        Predicate<String> effectiveValidator = validator != null ? validator : v -> true;
        if (!effectiveValidator.test(value)) {
            throw new IllegalArgumentException(buildLiteralOrDefaultError(valueName, raw));
        }
    }

    private static String buildPlaceholderSyntaxError(String valueName, String raw) {
        return "Invalid placeholder syntax for " + valueName + ": '" + raw + "'. "
                + "Expected ${name} or ${name:defaultValue}.";
    }

    private static String buildLiteralOrDefaultError(String valueName, String raw) {
        return "Invalid value for " + valueName + ": '" + raw + "'.";
    }

    private static String normalise(String s) {
        return s == null ? "" : s.trim();
    }

    // --------------------------------------------------------
    // Value Objects
    // --------------------------------------------------------

    public enum ValueType {
        EMPTY,
        LITERAL,
        PLACEHOLDER
    }

    /**
     * Result of parsing an input configuration value.
     *
     * <p>Exactly one of {@code literal} or {@code placeholder} will be present when type is LITERAL / PLACEHOLDER.
     * When type is EMPTY, neither is present.</p>
     */
    public static final class ConfigValue {

        private final ValueType type;
        private final String raw;
        private final String literal;
        private final Placeholder placeholder;

        private ConfigValue(ValueType type, String raw, String literal, Placeholder placeholder) {
            this.type = Objects.requireNonNull(type, "type");
            this.raw = raw;
            this.literal = literal;
            this.placeholder = placeholder;
        }

        public static ConfigValue empty(String raw) {
            return new ConfigValue(ValueType.EMPTY, raw, null, null);
        }

        public static ConfigValue literal(String raw, String literal) {
            return new ConfigValue(ValueType.LITERAL, raw, Objects.requireNonNull(literal, "literal"), null);
        }

        public static ConfigValue placeholder(String raw, Placeholder placeholder) {
            return new ConfigValue(ValueType.PLACEHOLDER, raw, null, Objects.requireNonNull(placeholder, "placeholder"));
        }

        public ValueType getType() {
            return type;
        }

        public String getRaw() {
            return raw;
        }

        public boolean isEmpty() {
            return type == ValueType.EMPTY;
        }

        public boolean isLiteral() {
            return type == ValueType.LITERAL;
        }

        public boolean isPlaceholder() {
            return type == ValueType.PLACEHOLDER;
        }

        public Optional<String> getLiteral() {
            return Optional.ofNullable(literal);
        }

        public Optional<Placeholder> getPlaceholder() {
            return Optional.ofNullable(placeholder);
        }
    }

    /**
     * Immutable representation of a parsed placeholder.
     *
     * <p>The {@code defaultValue} is:</p>
     * <ul>
     *   <li>{@code null} when no {@code :} is present (e.g. {@code ${name}})</li>
     *   <li>possibly empty when {@code :} is present (e.g. {@code ${name:}})</li>
     * </ul>
     */
    public static final class Placeholder {

        private final String name;
        private final String defaultValue;

        private Placeholder(String name, String defaultValue) {
            this.name = Objects.requireNonNull(name, "name");
            this.defaultValue = defaultValue;
        }

        public String getName() {
            return name;
        }

        public String getDefaultValue() {
            return defaultValue;
        }

        public boolean hasDefault() {
            return defaultValue != null;
        }
    }
}
