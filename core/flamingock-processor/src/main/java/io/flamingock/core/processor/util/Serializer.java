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
package io.flamingock.core.processor.util;

import com.fasterxml.jackson.databind.SerializationFeature;
import io.flamingock.internal.util.JsonObjectMapper;
import io.flamingock.internal.common.core.metadata.Constants;
import io.flamingock.internal.common.core.preview.CodePreviewChangeUnit;
import io.flamingock.internal.common.core.preview.PreviewPipeline;
import io.flamingock.internal.common.core.metadata.FlamingockMetadata;
import io.flamingock.internal.common.core.preview.PreviewStage;
import io.flamingock.internal.common.core.task.TaskDescriptor;

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
        serializeClassesList(metadata.getPipeline());
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

    private void serializeClassesList(PreviewPipeline pipeline) {
        writeToFile(Constants.FULL_GRAALVM_REFLECT_CLASSES_PATH, writer -> {
            if(pipeline.getSystemStage() != null) {
                serializeClassesFromStage(writer, pipeline.getSystemStage());;
            }

            for (PreviewStage stage : pipeline.getStages()) {
                serializeClassesFromStage(writer, stage);
            }
        });
    }

    private static void serializeClassesFromStage(Writer writer, PreviewStage stage) {
        for (TaskDescriptor task : stage.getTasks()) {

            if(CodePreviewChangeUnit.class.isAssignableFrom(task.getClass())) {
                try {
                    writer.write(task.getSource());
                    writer.write(System.lineSeparator());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
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
