/*
 * Copyright 2025 Flamingock (https://www.flamingock.io)
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
package io.flamingock.core.processor.template;

import io.flamingock.internal.common.core.template.TemplateMetadata;

import java.util.Collection;

/**
 * Interface for template discovery mechanisms.
 *
 * <p>Implementations of this interface are responsible for discovering
 * {@code @ChangeTemplate} annotated classes from various sources:
 * <ul>
 *   <li>Annotation processing: Discovers templates in the current compilation round</li>
 *   <li>File-based: Reads template class names from {@code META-INF/flamingock/templates}</li>
 * </ul>
 *
 * <p>This follows the Dependency Inversion Principle, allowing the
 * {@link TemplateDiscoveryOrchestrator} to work with any discovery mechanism.
 */
public interface ChangeTemplateDiscoverer {

    /**
     * Discovers templates and returns their metadata.
     *
     * @return collection of discovered template metadata
     */
    Collection<TemplateMetadata> discover();
}
