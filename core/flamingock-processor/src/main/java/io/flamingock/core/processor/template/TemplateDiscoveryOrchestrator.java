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
import io.flamingock.internal.common.core.util.LoggerPreProcessor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Orchestrates template discovery from multiple sources.
 *
 * <p>This orchestrator:
 * <ul>
 *   <li>Collects templates from all registered {@link ChangeTemplateDiscoverer} implementations</li>
 *   <li>Validates that template IDs are unique across all sources</li>
 *   <li>Warns about annotation-discovered templates not registered in any file</li>
 * </ul>
 *
 * <p>The orchestrator follows the Open/Closed Principle - new discoverers can be
 * added without modifying this class.
 */
public class TemplateDiscoveryOrchestrator {

    private final List<ChangeTemplateDiscoverer> discoverers;
    private final FileTemplateDiscoverer fileDiscoverer;
    private final LoggerPreProcessor logger;

    /**
     * Creates a new TemplateDiscoveryOrchestrator.
     *
     * @param discoverers    list of discoverers to use
     * @param fileDiscoverer the file-based discoverer (for checking unregistered templates)
     * @param logger         the logger for diagnostic messages
     */
    public TemplateDiscoveryOrchestrator(
            List<ChangeTemplateDiscoverer> discoverers,
            FileTemplateDiscoverer fileDiscoverer,
            LoggerPreProcessor logger) {
        this.discoverers = discoverers;
        this.fileDiscoverer = fileDiscoverer;
        this.logger = logger;
    }

    /**
     * Discovers all templates from all configured sources.
     *
     * @return the discovery result containing templates and any validation errors
     */
    public DiscoveryResult discoverAllTemplates() {
        logger.info("Discovering change templates");

        // 1. Discover from all sources
        Map<String, TemplateMetadata> templatesByFqcn = new HashMap<>();
        Map<String, TemplateMetadata> templatesById = new HashMap<>();
        List<String> errors = new ArrayList<>();

        for (ChangeTemplateDiscoverer discoverer : discoverers) {
            Collection<TemplateMetadata> discovered = discoverer.discover();

            for (TemplateMetadata template : discovered) {
                // Check for duplicate class names (same template discovered twice)
                String fqcn = template.getFullyQualifiedClassName();
                if (templatesByFqcn.containsKey(fqcn)) {
                    // Same class discovered by multiple discoverers - skip
                    continue;
                }

                // Check for duplicate IDs (different classes with same ID)
                String id = template.getId();
                if (templatesById.containsKey(id)) {
                    TemplateMetadata existing = templatesById.get(id);
                    errors.add("Duplicate template ID '" + id + "' found in classes: " +
                            existing.getFullyQualifiedClassName() + " and " + fqcn);
                    continue;
                }

                templatesByFqcn.put(fqcn, template);
                templatesById.put(id, template);
            }
        }

        // 2. Get registered class names from files (for warning)
        Set<String> fileRegisteredClassNames = fileDiscoverer.getRegisteredClassNames();

        // 3. Warn about annotation-discovered templates not in any file
        warnAboutUnregisteredTemplates(templatesByFqcn.values(), fileRegisteredClassNames);

        // 4. Return result
        List<TemplateMetadata> templates = new ArrayList<>(templatesById.values());
        logger.info("Discovered " + templates.size() + " templates total");

        return new DiscoveryResult(templates, errors);
    }

    /**
     * Warns about templates discovered via annotation but not registered in any file.
     *
     * @param templates               all discovered templates
     * @param fileRegisteredClassNames class names registered in template files
     */
    private void warnAboutUnregisteredTemplates(
            Collection<TemplateMetadata> templates,
            Set<String> fileRegisteredClassNames) {

        for (TemplateMetadata template : templates) {
            if (!fileRegisteredClassNames.contains(template.getFullyQualifiedClassName())) {
                logger.warn("Template '" + template.getId() + "' (class: " +
                        template.getFullyQualifiedClassName() + ") was discovered via annotation " +
                        "but is not listed in any META-INF/flamingock/templates file. " +
                        "If you externalize this template to a separate library, you must add it to that file.");
            }
        }
    }

    /**
     * Result of template discovery containing templates and any validation errors.
     */
    public static class DiscoveryResult {
        private final List<TemplateMetadata> templates;
        private final List<String> errors;

        public DiscoveryResult(List<TemplateMetadata> templates, List<String> errors) {
            this.templates = templates;
            this.errors = errors;
        }

        public List<TemplateMetadata> getTemplates() {
            return templates;
        }

        public List<String> getErrors() {
            return errors;
        }

        public boolean hasErrors() {
            return !errors.isEmpty();
        }
    }
}
