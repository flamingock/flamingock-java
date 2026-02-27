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
 * Contract for template payload types (APPLY and ROLLBACK generics).
 *
 * <p>All template payload types must implement this interface to enable
 * structural validation at pipeline load time, before any change executes.
 */
public interface TemplatePayload {

    /**
     * Validates this payload and returns any errors found.
     *
     * @return list of validation errors, empty if payload is valid
     */
    List<TemplatePayloadValidationError> validate();
}
