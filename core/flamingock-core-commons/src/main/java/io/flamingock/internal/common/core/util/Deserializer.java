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

import io.flamingock.internal.util.JsonObjectMapper;
import io.flamingock.internal.common.core.metadata.Constants;
import io.flamingock.internal.common.core.preview.PreviewPipeline;
import io.flamingock.internal.common.core.metadata.FlamingockMetadata;
import io.flamingock.internal.util.log.FlamingockLoggerFactory;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

public final class Deserializer {

    private static final Logger logger = FlamingockLoggerFactory.getLogger("Deserializer");

    private static final ClassLoader CLASS_LOADER = PreviewPipeline.class.getClassLoader();


    private Deserializer() {
    }

    public static FlamingockMetadata readMetadataFromFile() {
        return readMetadataOptional()
                .orElseThrow(() -> new RuntimeException("Flamingock metadata file not found"));
    }

    /**
     * Reads the preview pipeline from file. It first tries to load the full pipeline,
     * and if not found, falls back to the templated pipeline.
     *
     * @return PreviewPipeline object if found
     * @throws RuntimeException if neither file is found
     */
    public static PreviewPipeline readPreviewPipelineFromFile() {
        return readMetadataOptional()
                .map(FlamingockMetadata::getPipeline)
                .orElseThrow(() -> new RuntimeException("Flamingock metadata file not found"));
    }

    /**
     * Attempts to read a file and deserialize it into a PreviewPipeline.
     *
     * @return An Optional containing the deserialized PreviewPipeline if successful, otherwise empty
     */
    private static Optional<FlamingockMetadata> readMetadataOptional() {
        try (InputStream inputStream = CLASS_LOADER.getResourceAsStream(Constants.FULL_PIPELINE_FILE_PATH)) {
            if (inputStream == null) {
                logger.debug("Flamingock metadata file not found at the specified path: '{}'", Constants.FULL_PIPELINE_FILE_PATH);
                return Optional.empty();
            }

            FlamingockMetadata metadata = JsonObjectMapper.DEFAULT_INSTANCE.readValue(inputStream, FlamingockMetadata.class);
            logger.debug("Successfully deserialized Flamingock metadata from file: '{}'", Constants.FULL_PIPELINE_FILE_PATH);
            return Optional.of(metadata);
        } catch (IOException e) {
            throw new RuntimeException("Error reading Flamingock metadata file at: " + Constants.FULL_PIPELINE_FILE_PATH, e);
        }
    }

}
