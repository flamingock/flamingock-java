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

import java.util.List;

/**
 * Base contract for all template field types (configuration, apply, rollback).
 *
 * <p>Provides structural validation at pipeline load time, before any change executes.
 * Configuration fields extend this directly, while apply/rollback payloads extend
 * {@link TemplatePayload} which adds transactional metadata via {@code getInfo()}.
 */
public interface TemplateField {

    /**
     * Validates this field using the supplied change-level context
     * and returns any errors found.
     *
     * @param context change-level metadata available during validation
     * @return list of validation errors, empty if field is valid
     */
    List<TemplatePayloadValidationError> validate(TemplateValidationContext context);
}
