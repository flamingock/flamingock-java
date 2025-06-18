package io.flamingock.core.processor.util;

import io.flamingock.internal.util.JsonObjectMapper;
import io.flamingock.internal.common.core.metadata.Constants;
import io.flamingock.internal.common.core.preview.PreviewPipeline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

public final class Deserializer {

    private static final Logger logger = LoggerFactory.getLogger(Deserializer.class);

    private static final ClassLoader CLASS_LOADER = PreviewPipeline.class.getClassLoader();


    private Deserializer() {
    }

    /**
     * Reads the preview pipeline from file. It first tries to load the full pipeline,
     * and if not found, falls back to the templated pipeline.
     *
     * @return PreviewPipeline object if found
     * @throws RuntimeException if neither file is found
     */
    public static PreviewPipeline readPreviewPipelineFromFile() {
        return readFileIfExists(Constants.FULL_PIPELINE_FILE_PATH)
                .orElseGet(() -> readFileIfExists(Constants.TEMPLATED_PIPELINE_FILE_PATH)
                        .orElseThrow(() -> new RuntimeException("Flamingock Pipeline file not found")));
    }

    /**
     * Attempts to read a file and deserialize it into a PreviewPipeline.
     *
     * @param filePath Path to the file inside resources
     * @return An Optional containing the deserialized PreviewPipeline if successful, otherwise empty
     */
    private static Optional<PreviewPipeline> readFileIfExists(String filePath) {
        try (InputStream inputStream = CLASS_LOADER.getResourceAsStream(filePath)) {
            if (inputStream == null) {
                logger.debug("Flamingock pipeline file not found at the specified path: '{}'", filePath);
                return Optional.empty();
            }
            PreviewPipeline pipeline = JsonObjectMapper.DEFAULT_INSTANCE.readValue(inputStream, PreviewPipeline.class);
            logger.debug("Successfully deserialized Flamingock pipeline from file: '{}'", filePath);
            return Optional.of(pipeline);
        } catch (IOException e) {
            logger.error("Failed to read Flamingock pipeline file at '{}'.", filePath, e);
            throw new RuntimeException("Error reading file: " + filePath, e);
        }
    }

}
