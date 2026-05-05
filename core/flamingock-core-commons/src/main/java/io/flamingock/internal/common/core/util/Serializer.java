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
package io.flamingock.internal.common.core.util;

import com.fasterxml.jackson.databind.SerializationFeature;
import io.flamingock.internal.util.JsonObjectMapper;
import io.flamingock.internal.common.core.metadata.Constants;
import io.flamingock.internal.common.core.preview.CodePreviewChange;
import io.flamingock.internal.common.core.preview.PreviewPipeline;
import io.flamingock.internal.common.core.metadata.FlamingockMetadata;
import io.flamingock.internal.common.core.preview.PreviewStage;
import io.flamingock.internal.common.core.change.ChangeDescriptor;

import javax.annotation.processing.ProcessingEnvironment;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.Writer;
import java.util.function.Consumer;

public class Serializer {

    private final ProcessingEnvironment processingEnv;
    private final LoggerPreProcessor logger;

    public Serializer(ProcessingEnvironment processingEnv, LoggerPreProcessor logger) {
        this.processingEnv = processingEnv;
        this.logger = logger;
    }


    public void serializeFullPipeline(FlamingockMetadata metadata) {
        serializePipelineTo(metadata);
        serializeClassesList(metadata);
    }

    private void serializePipelineTo(FlamingockMetadata metadata) {
        writeToFile(Constants.FULL_PIPELINE_FILE_PATH, writer -> {
            try {
                writer.write(JsonObjectMapper.DEFAULT_INSTANCE.enable(SerializationFeature.INDENT_OUTPUT).writeValueAsString(metadata));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private void serializeClassesList(FlamingockMetadata metadata) {
        // Collect classnames into a stable-ordered set first to dedup. The pipeline can carry
        // the same class via multiple paths (e.g. orphan + builder provider) and successive
        // commits during a build could otherwise produce duplicate lines in the file.
        java.util.LinkedHashSet<String> classNames = new java.util.LinkedHashSet<>();
        if (metadata.hasValidBuilderProvider()) {
            classNames.add(metadata.getBuilderProvider().getClassName());
        }
        PreviewPipeline pipeline = metadata.getPipeline();
        if (pipeline != null) {
            if (pipeline.getSystemStage() != null) {
                collectClassNamesFromStage(pipeline.getSystemStage(), classNames);
            }
            if (pipeline.getStages() != null) {
                for (PreviewStage stage : pipeline.getStages()) {
                    collectClassNamesFromStage(stage, classNames);
                }
            }
        }
        if (metadata.getOrphanChanges() != null) {
            for (CodePreviewChange orphan : metadata.getOrphanChanges()) {
                if (orphan.getSource() != null) {
                    classNames.add(orphan.getSource());
                }
            }
        }

        writeToFile(Constants.FULL_GRAALVM_REFLECT_CLASSES_PATH, writer -> {
            try {
                for (String name : classNames) {
                    writer.write(name);
                    writer.write(System.lineSeparator());
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private static void collectClassNamesFromStage(PreviewStage stage, java.util.Set<String> sink) {
        if (stage.getChanges() == null) return;
        for (ChangeDescriptor change : stage.getChanges()) {
            if (CodePreviewChange.class.isAssignableFrom(change.getClass())
                    && change.getSource() != null) {
                sink.add(change.getSource());
            }
        }
    }

    private void writeToFile(String filePath, Consumer<Writer> writerConsumer) {

        FileObject file;
        try {
            file = processingEnv.getFiler().createResource(StandardLocation.CLASS_OUTPUT, "", filePath);
        } catch (IOException e) {
            logger.error("Failed to creating flamingock metadata file: " + e.getMessage());
            throw new RuntimeException(e);
        }
        try (Writer writer = file.openWriter()) {
            writerConsumer.accept(writer);
        } catch (IOException e) {
            logger.error("Failed to write AnnotatedClasses file: " + e.getMessage());
            throw new RuntimeException("Failed to write AnnotatedClasses file: " + e.getMessage());
        }

    }
}
