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
package io.flamingock.internal.common.core.template;

import io.flamingock.api.template.ChangeTemplate;

/**
 * Wraps a template class together with its pre-resolved metadata from the {@code @ChangeTemplate} annotation.
 * <p>
 * Created at registration time in {@link ChangeTemplateManager}, this ensures:
 * <ul>
 *   <li>The {@code @ChangeTemplate} annotation is validated once at registration (fail-fast if missing)</li>
 *   <li>The {@code multiStep} flag is resolved once and exposed via this wrapper</li>
 *   <li>Consumers never need to read annotations directly</li>
 * </ul>
 */
public class ChangeTemplateDefinition {

    private final Class<? extends ChangeTemplate<?, ?, ?>> templateClass;
    private final boolean multiStep;

    public ChangeTemplateDefinition(
            Class<? extends ChangeTemplate<?, ?, ?>> templateClass,
            boolean multiStep) {
        this.templateClass = templateClass;
        this.multiStep = multiStep;
    }

    public String getId() {
        return getTemplateClass().getSimpleName();
    }

    public Class<? extends ChangeTemplate<?, ?, ?>> getTemplateClass() {
        return templateClass;
    }

    public boolean isMultiStep() {
        return multiStep;
    }
}
