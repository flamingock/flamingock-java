package io.flamingock.core.processor.util;

import com.fasterxml.jackson.databind.SerializationFeature;
import io.flamingock.commons.utils.JsonObjectMapper;
import io.flamingock.core.metadata.Constants;
import io.flamingock.core.preview.CodePreviewChangeUnit;
import io.flamingock.core.preview.PreviewPipeline;
import io.flamingock.core.preview.PreviewStage;
import io.flamingock.core.task.TaskDescriptor;

import javax.annotation.processing.ProcessingEnvironment;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.Writer;
import java.util.function.Consumer;

public class Serializer {
    //requires JdkModule for optionals

    private final ProcessingEnvironment processingEnv;
    private final LoggerPreProcessor logger;

    public Serializer(ProcessingEnvironment processingEnv, LoggerPreProcessor logger) {
        this.processingEnv = processingEnv;
        this.logger = logger;
    }

    public void serializeTemplatedPipeline(PreviewPipeline pipeline) {
        serializePipelineTo(pipeline, Constants.TEMPLATED_PIPELINE_FILE_PATH);
        serializeClassesList(pipeline, Constants.TEMPLATED_GRAALVM_REFLECT_CLASSES_PATH);
    }

    public void serializeFullPipeline(PreviewPipeline pipeline) {
        serializePipelineTo(pipeline, Constants.FULL_PIPELINE_FILE_PATH);
        serializeClassesList(pipeline, Constants.FULL_GRAALVM_REFLECT_CLASSES_PATH);
    }

    private void serializePipelineTo(PreviewPipeline pipeline, String filePath) {
        writeToFile(filePath, writer -> {
            try {
                writer.write(JsonObjectMapper.DEFAULT_INSTANCE.enable(SerializationFeature.INDENT_OUTPUT).writeValueAsString(pipeline));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private void serializeClassesList(PreviewPipeline pipeline, String filePath) {
        writeToFile(filePath, writer -> {
            for (PreviewStage stage : pipeline.getStages()) {
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
        });
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
