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
 * A {@link TemplatePayload} sentinel representing "no configuration needed".
 *
 * <p>Replaces {@code Void} as the CONFIG type parameter in templates that
 * have no shared configuration. Unlike {@code Void}, this class implements
 * {@code TemplatePayload}, satisfying the {@code CONFIG extends TemplatePayload}
 * bound on the template system.
 */
public class TemplateVoid implements TemplatePayload {

    @Override
    public List<TemplatePayloadValidationError> validate() {
        return Collections.emptyList();
    }
}
