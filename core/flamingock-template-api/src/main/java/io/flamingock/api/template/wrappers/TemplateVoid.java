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
import io.flamingock.api.template.TemplatePayloadInfo;
import io.flamingock.api.template.TemplatePayloadValidationError;
import io.flamingock.api.template.TemplateValidationContext;

import java.util.Collections;
import java.util.List;

/**
 * A {@link TemplatePayload} sentinel representing "no value needed".
 *
 * <p>Replaces {@code Void} as a type parameter in templates that have no shared
 * configuration or no rollback. Implements {@code TemplatePayload} so it can be
 * used in any template type parameter position (CONFIG, APPLY, or ROLLBACK).
 */
public class TemplateVoid implements TemplatePayload {

    private static final TemplatePayloadInfo TEMPLATE_PAYLOAD_INFO = new TemplatePayloadInfo(false);

    @Override
    public List<TemplatePayloadValidationError> validate(TemplateValidationContext context) {
        return Collections.emptyList();
    }

    @Override
    public TemplatePayloadInfo getInfo() {
        return TEMPLATE_PAYLOAD_INFO;
    }
}
