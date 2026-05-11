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

import io.flamingock.internal.common.core.metadata.FlamingockMetadata;
import io.flamingock.internal.common.core.util.LoggerPreProcessor;
import io.flamingock.internal.common.core.util.Serializer;
import io.flamingock.internal.util.JsonObjectMapper;

import javax.annotation.processing.ProcessingEnvironment;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.NoSuchFileException;
import java.util.HashMap;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Lazy, in-memory cache of {@link FlamingockMetadata} for a single annotation processing pass.
 *
 * <p>Reads {@code META-INF/flamingock/metadata.json} from the {@link javax.annotation.processing.Filer}
 * on first {@link #update}, otherwise starts from an empty {@code FlamingockMetadata}. Writes the
 * file via {@link Serializer} only when {@link #commit()} is called and the cache is dirty.
 *
 * <p>Designed to make the annotation processor incremental-build aware: a round that sees only a
 * subset of annotated elements upserts into the existing file rather than overwriting it.
 */
public final class FlamingockMetadataStore {

    private final ProcessingEnvironment processingEnv;
    private final LoggerPreProcessor logger;
    private final String metadataResourcePath;
    private final String reflectClassesResourcePath;

    private FlamingockMetadata cached;
    private boolean loaded = false;
    private boolean dirty = false;

    /**
     * @param metadataResourcePath module-unique JSON resource path to read/write the metadata
     *                             at (e.g. {@code META-INF/flamingock/metadata_<suffix>.json}).
     * @param reflectClassesResourcePath module-unique reflection-classes file path written
     *                                   alongside the metadata for GraalVM native image support.
     */
    public FlamingockMetadataStore(ProcessingEnvironment processingEnv,
                                   LoggerPreProcessor logger,
                                   String metadataResourcePath,
                                   String reflectClassesResourcePath) {
        this.processingEnv = processingEnv;
        this.logger = logger;
        this.metadataResourcePath = metadataResourcePath;
        this.reflectClassesResourcePath = reflectClassesResourcePath;
    }

    /**
     * Lazy-load on first call. Mutate the metadata via {@code updater}; the store is marked dirty.
     */
    public void update(Consumer<FlamingockMetadata> updater) {
        ensureLoaded();
        updater.accept(cached);
        dirty = true;
    }

    /**
     * Read-only snapshot of the cached metadata. Lazy-loads but does not mark the store dirty.
     */
    public Optional<FlamingockMetadata> peek() {
        ensureLoaded();
        return Optional.ofNullable(cached);
    }

    /**
     * Mark the store dirty without going through {@link #update(Consumer)}. Use when an
     * external helper has already mutated the cached metadata in place (e.g. last-round
     * pruning) and we just need the next {@link #commit()} to write it out.
     */
    public void markDirty() {
        ensureLoaded();
        dirty = true;
    }

    /** Writes the cached metadata via {@link Serializer} only when dirty. Idempotent. */
    public void commit() {
        if (!dirty) {
            return;
        }
        new Serializer(processingEnv, logger)
                .serializeFullPipeline(cached, metadataResourcePath, reflectClassesResourcePath);
        dirty = false;
    }

    private void ensureLoaded() {
        if (loaded) {
            return;
        }
        cached = readExisting().orElseGet(FlamingockMetadata::new);
        if (cached.getProperties() == null) {
            cached.setProperties(new HashMap<>());
        }
        loaded = true;
    }

    private Optional<FlamingockMetadata> readExisting() {
        try {
            FileObject file = processingEnv.getFiler().getResource(
                    StandardLocation.CLASS_OUTPUT, "", metadataResourcePath);
            try (InputStream in = file.openInputStream()) {
                FlamingockMetadata metadata = JsonObjectMapper.DEFAULT_INSTANCE
                        .readValue(in, FlamingockMetadata.class);
                logger.verbose("Loaded existing Flamingock metadata for incremental update");
                return Optional.of(metadata);
            }
        } catch (FileNotFoundException | NoSuchFileException e) {
            return Optional.empty();
        } catch (IOException | IllegalArgumentException | IllegalStateException e) {
            // Some Filer implementations throw differently when the file is absent or when
            // CLASS_OUTPUT is not open for reading. Degrade to "no existing file" — worst case
            // is the legacy overwrite behavior, which is no regression.
            logger.verbose("Existing Flamingock metadata not readable from Filer: " + e.getMessage());
            return Optional.empty();
        }
    }
}
