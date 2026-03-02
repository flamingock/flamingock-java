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
package io.flamingock.support.mongock.processor;

import com.github.cloudyrock.mongock.ChangeLog;
import io.flamingock.internal.common.core.audit.AuditWriter;
import io.flamingock.internal.common.core.processor.AnnotationProcessorPlugin;
import io.flamingock.internal.common.core.processor.ChangeDiscoverer;
import io.flamingock.internal.common.core.error.FlamingockException;
import io.flamingock.internal.common.core.pipeline.PipelineDescriptor;
import io.flamingock.internal.common.core.preview.CodePreviewChange;
import io.flamingock.internal.common.core.preview.PreviewConstructor;
import io.flamingock.internal.common.core.preview.PreviewMethod;
import io.flamingock.internal.common.core.preview.builder.CodePreviewTaskBuilder;
import io.flamingock.internal.common.core.processor.ConfigurationPropertiesProvider;
import io.flamingock.internal.common.core.task.RecoveryDescriptor;
import io.flamingock.internal.common.core.task.TargetSystemDescriptor;
import io.flamingock.internal.common.core.util.ConfigValueParser;
import io.flamingock.internal.common.core.util.LoggerPreProcessor;
import io.flamingock.internal.core.external.targets.TargetSystemManager;
import io.flamingock.support.mongock.MongockImportChange;
import io.flamingock.support.mongock.annotations.MongockSupport;
import io.flamingock.support.mongock.internal.preview.builder.MongockCodePreviewChangeHelper;
import io.mongock.api.annotations.ChangeUnit;

import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.flamingock.internal.common.core.metadata.Constants.MONGOCK_IMPORT_EMPTY_ORIGIN_ALLOWED_PROPERTY_KEY;
import static io.flamingock.internal.common.core.metadata.Constants.MONGOCK_IMPORT_SKIP_PROPERTY_KEY;
import static io.flamingock.internal.common.core.metadata.Constants.MONGOCK_IMPORT_ORIGIN_PROPERTY_KEY;

@SuppressWarnings("deprecation")
public class MongockAnnotationProcessorPlugin implements AnnotationProcessorPlugin, ChangeDiscoverer, ConfigurationPropertiesProvider {

    private RoundEnvironment roundEnv;
    private LoggerPreProcessor logger;
    private MongockSupport mongockSupport;

    @Override
    public void initialize(RoundEnvironment roundEnv, LoggerPreProcessor logger) {
        this.roundEnv = roundEnv;
        this.logger = logger;
        Optional<MongockSupport> mongockSupportOpt = this.getMongockSupportAnnotation(roundEnv, logger);
        logger.info(String.format("Searching for @MongockSupport annotation: %s", mongockSupportOpt.isPresent() ? "Found" : "Not found"));
        mongockSupport = mongockSupportOpt.orElseThrow(() -> new FlamingockException("@MongockSupport annotation must be provided when mongock-support module is present."));
    }

    @Override
    public Collection<CodePreviewChange> findAnnotatedChanges() {

        final String mongockTargetSystemId = mongockSupport.targetSystem();

        logger.info("Searching for code-based changes (Java classes annotated with @ChangeUnit or @ChangeLog annotations)");
        List<CodePreviewChange> changes = Stream.concat(
                        roundEnv.getElementsAnnotatedWith(ChangeUnit.class).stream(),
                        roundEnv.getElementsAnnotatedWith(ChangeLog.class).stream()
                )
                .filter(e -> e.getKind() == ElementKind.CLASS)
                .map(e -> (TypeElement) e)
                .map(e -> buildCodePreviewChange(e, mongockTargetSystemId))
                .flatMap(List::stream)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        changes.add(getImporterChange(mongockTargetSystemId));

        return changes;
    }

    @Override
    public Map<String, String> getConfigurationProperties() {

        Map<String, String> properties = new HashMap<>();

        // Adding Mongock specific configuration properties
        processConfigurationProperties(mongockSupport, properties);

        return properties;
    }


    private CodePreviewChange getImporterChange(String targetSystemId) {
        CodePreviewTaskBuilder builder = CodePreviewTaskBuilder.instance();
        builder.setId("migration-mongock-to-flamingock-community");
        builder.setOrder("00100");
        builder.setAuthor("flamingock-team");
        builder.setTargetSystem(new TargetSystemDescriptor(targetSystemId));
        builder.setTransactional(true);
        builder.setSystem(true);
        builder.setRecovery(RecoveryDescriptor.getDefault());


        builder.setSourceClassPath(MongockImportChange.class.getName());
        builder.setConstructor(PreviewConstructor.getDefault());
        List<String> applyParameterTypes = Arrays.asList(
                String.class.getName(),
                TargetSystemManager.class.getName(),
                AuditWriter.class.getName(),
                PipelineDescriptor.class.getName()
        );
        builder.setApplyMethod(new PreviewMethod("importHistory", applyParameterTypes));
        return builder.build();
    }

    private Optional<MongockSupport> getMongockSupportAnnotation(RoundEnvironment roundEnv, LoggerPreProcessor logger) {
        return roundEnv.getElementsAnnotatedWith(MongockSupport.class)
                .stream()
                .filter(e -> e.getKind() == ElementKind.CLASS)
                .map(e -> (TypeElement) e)
                .map(e -> e.getAnnotation(MongockSupport.class))
                .findFirst();
    }

    private List<CodePreviewChange> buildCodePreviewChange(TypeElement typeElement, String mongockTargetSystemId) {
        return new MongockCodePreviewChangeHelper().getCodePreviewChanges(typeElement, mongockTargetSystemId);
    }

    private void processConfigurationProperties(MongockSupport mongockSupport, Map<String, String> properties) {
        if (properties == null) {
            throw new IllegalArgumentException("properties");
        }

        // Skip Import
        ConfigValueParser.ConfigValue skipImportValue =
                ConfigValueParser.parse("skipImport", mongockSupport.skipImport(), ConfigValueParser.BOOLEAN_VALUE_VALIDATOR);
        if (!skipImportValue.isEmpty()) {
            properties.put(MONGOCK_IMPORT_SKIP_PROPERTY_KEY, skipImportValue.getRaw());
        }

        // Empty Origin Allowed
        ConfigValueParser.ConfigValue emptyOriginAllowedValue =
                ConfigValueParser.parse("emptyOriginAllowed", mongockSupport.emptyOriginAllowed(), ConfigValueParser.BOOLEAN_VALUE_VALIDATOR);
        if (!emptyOriginAllowedValue.isEmpty()) {
            properties.put(MONGOCK_IMPORT_EMPTY_ORIGIN_ALLOWED_PROPERTY_KEY, emptyOriginAllowedValue.getRaw());
        }

        // Origin
        ConfigValueParser.ConfigValue originValue =
                ConfigValueParser.parse("origin", mongockSupport.origin());
        if (!originValue.isEmpty()) {
            properties.put(MONGOCK_IMPORT_ORIGIN_PROPERTY_KEY, originValue.getRaw());
        }
    }
}
