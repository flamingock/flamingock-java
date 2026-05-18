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

import io.flamingock.internal.common.core.util.LoggerPreProcessor;

import javax.annotation.processing.ProcessingEnvironment;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.List;
import java.util.Optional;

/**
 * Per-module identity for the Flamingock metadata provider that the annotation processor
 * generates. Pairs a generated provider class FQN with its metadata file resource path; the
 * two share a unique suffix so reading the SPI file is sufficient to know both.
 *
 * <p>Resolution strategy: read {@code META-INF/services/...FlamingockMetadataProvider} from
 * {@code CLASS_OUTPUT} via the Filer; if it already lists a class, derive the suffix from
 * its FQN and reuse. Otherwise generate a fresh suffix via {@link SecureRandom}. Whether the
 * identity is freshly generated or reused is exposed via {@link #isPersisted()} so the
 * caller knows whether to write the SPI file + provider source.
 *
 * <p>This class is an immutable value object; the I/O side-effects live on the static
 * factory methods.
 */
public final class MetadataModuleIdentity {

    /** SPI registration file path. Stable string; no version. */
    public static final String SPI_FILE_PATH =
            "META-INF/services/io.flamingock.internal.common.core.metadata.FlamingockMetadataProvider";

    /** Package + simple-name prefix for the generated provider class. */
    static final String GENERATED_PACKAGE = "io.flamingock.generated";
    static final String GENERATED_CLASS_PREFIX = "FlamingockMetadataProviderImpl_";

    /** Template for the metadata file path. {@code <suffix>} is the 8-hex module identifier. */
    static final String METADATA_FILE_PREFIX = "META-INF/flamingock/metadata_";
    static final String METADATA_FILE_SUFFIX_EXT = ".json";

    /** Template for the GraalVM reflection-classes file. */
    static final String REFLECT_FILE_PREFIX = "META-INF/flamingock/reflection-classes_";
    static final String REFLECT_FILE_SUFFIX_EXT = ".txt";

    private static final int SUFFIX_HEX_CHARS = 8;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final String suffix;
    private final boolean persisted;

    private MetadataModuleIdentity(String suffix, boolean persisted) {
        this.suffix = suffix;
        this.persisted = persisted;
    }

    public String getSuffix() {
        return suffix;
    }

    public String getProviderClassFqn() {
        return GENERATED_PACKAGE + "." + GENERATED_CLASS_PREFIX + suffix;
    }

    public String getProviderSimpleClassName() {
        return GENERATED_CLASS_PREFIX + suffix;
    }

    public String getMetadataResourcePath() {
        return METADATA_FILE_PREFIX + suffix + METADATA_FILE_SUFFIX_EXT;
    }

    public String getReflectClassesResourcePath() {
        return REFLECT_FILE_PREFIX + suffix + REFLECT_FILE_SUFFIX_EXT;
    }

    /**
     * Whether the identity was discovered from an already-written SPI file. When false, the
     * caller is expected to persist (write the SPI file + generate the provider source) at
     * the appropriate point in {@code process()}.
     */
    public boolean isPersisted() {
        return persisted;
    }

    /**
     * Disk-based discovery: given a build's class-output directory, return the per-module
     * identity by parsing the generated SPI file. Returns {@link Optional#empty()} when no
     * SPI file exists (i.e. the build was a no-op for Flamingock).
     *
     * <p>Intended for tests and tooling that need to find the module's metadata file without
     * going through the {@link javax.annotation.processing.Filer} API.
     */
    public static Optional<MetadataModuleIdentity> discoverFromClassOutput(Path classOutputDir)
            throws IOException {
        Path spiFile = classOutputDir.resolve(SPI_FILE_PATH);
        if (!Files.exists(spiFile)) {
            return Optional.empty();
        }
        List<String> lines = Files.readAllLines(spiFile, StandardCharsets.UTF_8);
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                String suffix = extractSuffix(trimmed);
                if (suffix == null) {
                    throw new IOException("Unexpected SPI registration FQN: " + trimmed);
                }
                return Optional.of(new MetadataModuleIdentity(suffix, true));
            }
        }
        return Optional.empty();
    }

    /**
     * Read-or-generate the identity for the module currently being compiled.
     */
    public static MetadataModuleIdentity resolve(ProcessingEnvironment processingEnv,
                                                 LoggerPreProcessor logger) {
        String existingFqn = readExistingProviderFqn(processingEnv, logger);
        if (existingFqn != null) {
            String suffix = extractSuffix(existingFqn);
            if (suffix != null) {
                logger.verbose("Reusing existing Flamingock provider identity: " + existingFqn);
                return new MetadataModuleIdentity(suffix, true);
            }
            logger.warn("Existing SPI file declares an unrecognized class FQN '" + existingFqn
                    + "'; regenerating with a fresh suffix.");
        }
        String fresh = generateSuffix();
        logger.verbose("Generated new Flamingock provider identity (suffix=" + fresh + ")");
        return new MetadataModuleIdentity(fresh, false);
    }

    private static String readExistingProviderFqn(ProcessingEnvironment processingEnv,
                                                  LoggerPreProcessor logger) {
        try {
            FileObject file = processingEnv.getFiler().getResource(
                    StandardLocation.CLASS_OUTPUT, "", SPI_FILE_PATH);
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(file.openInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim();
                    if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                        return trimmed;
                    }
                }
                return null;
            }
        } catch (FileNotFoundException | NoSuchFileException e) {
            return null;
        } catch (IOException | IllegalArgumentException | IllegalStateException e) {
            // Same defensive degrade as FlamingockMetadataStore: some Filer impls behave
            // differently when the file is absent or CLASS_OUTPUT is not open for reading.
            logger.verbose("Existing SPI file not readable from Filer: " + e.getMessage());
            return null;
        }
    }

    /**
     * Pull the suffix back out of a class FQN of the form
     * {@code io.flamingock.generated.FlamingockMetadataProviderImpl_<suffix>}.
     */
    private static String extractSuffix(String fqn) {
        String expectedPrefix = GENERATED_PACKAGE + "." + GENERATED_CLASS_PREFIX;
        if (!fqn.startsWith(expectedPrefix)) {
            return null;
        }
        String suffix = fqn.substring(expectedPrefix.length());
        if (suffix.isEmpty()) {
            return null;
        }
        return suffix;
    }

    private static String generateSuffix() {
        byte[] bytes = new byte[SUFFIX_HEX_CHARS / 2];
        RANDOM.nextBytes(bytes);
        StringBuilder hex = new StringBuilder(SUFFIX_HEX_CHARS);
        for (byte b : bytes) {
            hex.append(String.format("%02x", b & 0xff));
        }
        return hex.toString();
    }
}
