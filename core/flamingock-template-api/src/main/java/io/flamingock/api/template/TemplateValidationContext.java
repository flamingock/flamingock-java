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
package io.flamingock.api.template;

/**
 * Context provided to {@link TemplatePayload#validate(TemplateValidationContext)}
 * so payload validation can access change-level metadata.
 *
 * <p><b>Binary-compatibility contract:</b> new fields are added as
 * getter/setter pairs with sensible defaults.  Older template payloads
 * that ignore the new fields continue to work unchanged.
 *
 * <p>Current fields:
 * <ul>
 *   <li>{@code transactional} (default {@code false}) — whether the
 *       enclosing change is declared transactional.</li>
 * </ul>
 */
public class TemplateValidationContext {

    private boolean transactional;

    /**
     * Creates a context with all fields set to their defaults.
     */
    public TemplateValidationContext() {
    }

    /**
     * Returns whether the enclosing change is declared transactional.
     *
     * @return {@code true} if the change is transactional, {@code false} otherwise
     */
    public boolean isTransactional() {
        return transactional;
    }

    /**
     * Sets whether the enclosing change is declared transactional.
     *
     * @param transactional {@code true} if the change is transactional
     */
    public void setTransactional(boolean transactional) {
        this.transactional = transactional;
    }
}
