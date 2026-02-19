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

import io.flamingock.api.annotations.Apply;
import io.flamingock.api.annotations.ChangeTemplate;
import io.flamingock.api.template.AbstractChangeTemplate;

/**
 * Test steppable template for SPI loading tests.
 * This template is registered via META-INF/services/io.flamingock.api.template.ChangeTemplate
 * and is used to verify that ChangeTemplateManager correctly discovers steppable templates via ServiceLoader.
 */
@ChangeTemplate(id = "SPITestSteppableTemplate", multiStep = true)
public class SPITestSteppableTemplate extends AbstractChangeTemplate<Void, String, String> {

    public SPITestSteppableTemplate() {
        super();
    }

    @Apply
    public void apply() {
        // Test implementation - does nothing
    }
}
