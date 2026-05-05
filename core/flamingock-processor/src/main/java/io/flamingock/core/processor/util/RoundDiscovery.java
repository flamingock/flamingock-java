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
package io.flamingock.core.processor.util;

import io.flamingock.internal.common.core.processor.ConfigurationPropertiesProvider;
import io.flamingock.internal.common.core.util.LoggerPreProcessor;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Coordinates {@link PluginFinder} and {@link AnnotationFinder} to produce a single
 * {@link RoundInputs} snapshot for the current compilation round.
 */
public final class RoundDiscovery {

    private final ProcessingEnvironment processingEnv;
    private final LoggerPreProcessor logger;

    public RoundDiscovery(ProcessingEnvironment processingEnv, LoggerPreProcessor logger) {
        this.processingEnv = processingEnv;
        this.logger = logger;
    }

    public RoundInputs discover(RoundEnvironment roundEnv) {
        PluginFinder pluginFinder = new PluginFinder();
        AnnotationFinder annotationFinder = new AnnotationFinder(roundEnv, logger, processingEnv);
        pluginFinder.loadAndInitializePlugins(roundEnv, logger);

        return new RoundInputs(
                annotationFinder.getPipelineAnnotation(),
                annotationFinder.findAnnotatedChanges(pluginFinder.getChangeDiscoverers()),
                annotationFinder.findBuilderProvider(),
                collectPluginProperties(pluginFinder.getConfigurationPropertiesProviders()));
    }

    private static Map<String, String> collectPluginProperties(List<ConfigurationPropertiesProvider> providers) {
        Map<String, String> properties = new HashMap<>();
        providers.stream()
                .map(ConfigurationPropertiesProvider::getConfigurationProperties)
                .flatMap(m -> m.entrySet().stream())
                .forEach(e -> properties.put(e.getKey(), e.getValue()));
        return properties;
    }
}
