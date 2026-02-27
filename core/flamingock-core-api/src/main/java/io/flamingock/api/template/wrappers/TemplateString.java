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
package io.flamingock.api.template.wrappers;

import io.flamingock.api.template.TemplatePayload;
import io.flamingock.api.template.TemplatePayloadValidationError;

import java.util.Collections;
import java.util.List;

/**
 * A {@link TemplatePayload} wrapper for {@code String} payloads.
 *
 * <p>Provides a drop-in replacement for raw {@code String} payloads in templates,
 * keeping YAML clean while satisfying the {@code TemplatePayload} contract.
 *
 * <p>Supports SnakeYAML deserialization via the no-arg constructor and scalar
 * conversion via the {@code String} constructor.
 */
public class TemplateString implements TemplatePayload {

    private String value;

    /**
     * No-arg constructor for SnakeYAML deserialization.
     */
    public TemplateString() {
    }

    /**
     * Constructor for scalar conversion (e.g., from YAML string values).
     *
     * @param value the string value
     */
    public TemplateString(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    @Override
    public List<TemplatePayloadValidationError> validate() {
        if (value == null || value.trim().isEmpty()) {
            return Collections.singletonList(
                    new TemplatePayloadValidationError("value", "must not be null or blank"));
        }
        return Collections.emptyList();
    }

    @Override
    public String toString() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TemplateString that = (TemplateString) o;
        return value != null ? value.equals(that.value) : that.value == null;
    }

    @Override
    public int hashCode() {
        return value != null ? value.hashCode() : 0;
    }
}
