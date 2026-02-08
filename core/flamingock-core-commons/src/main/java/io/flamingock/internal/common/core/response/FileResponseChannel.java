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
package io.flamingock.internal.common.core.response;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * Response channel implementation that writes to a file.
 * Uses atomic write (temp file + rename) to ensure consistency.
 */
public class FileResponseChannel implements ResponseChannel {

    private final Path outputPath;
    private final ObjectMapper objectMapper;

    public FileResponseChannel(String outputFilePath, ObjectMapper objectMapper) {
        this.outputPath = Paths.get(outputFilePath);
        this.objectMapper = objectMapper;
    }

    @Override
    public void write(ResponseEnvelope envelope) throws ResponseChannelException {
        Path tempFile = null;
        try {
            Path parentDir = outputPath.getParent();
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
            }

            tempFile = Files.createTempFile(
                    parentDir != null ? parentDir : Paths.get(System.getProperty("java.io.tmpdir")),
                    "flamingock-response-",
                    ".tmp"
            );

            objectMapper.writerWithDefaultPrettyPrinter().writeValue(tempFile.toFile(), envelope);

            Files.move(tempFile, outputPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

        } catch (IOException e) {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {
                }
            }
            throw new ResponseChannelException("Failed to write response to file: " + outputPath, e);
        }
    }

    @Override
    public void close() {
    }
}
