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

import io.flamingock.api.StageType;
import io.flamingock.internal.common.core.metadata.FlamingockMetadata;
import io.flamingock.internal.common.core.preview.CodePreviewChange;
import io.flamingock.internal.common.core.preview.PreviewPipeline;
import io.flamingock.internal.common.core.preview.PreviewStage;
import io.flamingock.internal.common.core.util.LoggerPreProcessor;
import io.flamingock.internal.util.JsonObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Writer;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FlamingockMetadataStoreTest {

    /** Stable per-module test paths. Real builds get unique suffixes; tests can use anything. */
    private static final String METADATA_PATH = "META-INF/flamingock/metadata_test01.json";
    private static final String REFLECT_PATH = "META-INF/flamingock/reflection-classes_test01.txt";

    @TempDir
    Path tempDir;

    @Test
    void firstUpdateLazyLoadsExistingFile() throws IOException {
        FlamingockMetadata existing = new FlamingockMetadata();
        existing.setPipelineFile("flamingock/pipeline.yaml");
        Map<String, String> props = new HashMap<>();
        props.put("k", "v");
        existing.setProperties(props);

        Path file = tempDir.resolve("metadata.json");
        Files.write(file, JsonObjectMapper.DEFAULT_INSTANCE.writeValueAsBytes(existing));

        FakeFiler filer = new FakeFiler(file);
        ProcessingEnvironment env = mockEnv(filer);

        FlamingockMetadataStore store = new FlamingockMetadataStore(env, new LoggerPreProcessor(env), METADATA_PATH, REFLECT_PATH);
        store.update(metadata -> {
            assertEquals("flamingock/pipeline.yaml", metadata.getPipelineFile());
            assertEquals("v", metadata.getProperties().get("k"));
        });
    }

    @Test
    void firstUpdateOnMissingFileStartsFromEmptyMetadata() {
        FakeFiler filer = new FakeFiler(tempDir.resolve("does-not-exist.json"));
        ProcessingEnvironment env = mockEnv(filer);

        FlamingockMetadataStore store = new FlamingockMetadataStore(env, new LoggerPreProcessor(env), METADATA_PATH, REFLECT_PATH);
        store.update(metadata -> {
            assertNotNull(metadata);
            assertFalse(metadata.hasValidBuilderProvider());
            assertNotNull(metadata.getProperties(), "properties map should be initialized to empty");
            assertTrue(metadata.getProperties().isEmpty());
        });
    }

    @Test
    void commitWritesOnlyWhenDirty() throws IOException {
        Path readPath = tempDir.resolve("does-not-exist.json");
        FakeFiler filer = new FakeFiler(readPath, tempDir);
        ProcessingEnvironment env = mockEnv(filer);

        FlamingockMetadataStore store = new FlamingockMetadataStore(env, new LoggerPreProcessor(env), METADATA_PATH, REFLECT_PATH);

        Path metadataOut = tempDir.resolve(METADATA_PATH);

        // No update called → commit must be a no-op
        store.commit();
        assertFalse(Files.exists(metadataOut), "commit without update must not write");

        // Trigger a dirty update and commit
        store.update(metadata -> metadata.setPipeline(samplePipeline()));
        store.commit();
        assertTrue(Files.exists(metadataOut), "commit after update must write metadata.json");

        FlamingockMetadata read = JsonObjectMapper.DEFAULT_INSTANCE.readValue(metadataOut.toFile(), FlamingockMetadata.class);
        assertNotNull(read.getPipeline());
        assertEquals(1, read.getPipeline().getStages().size());
    }

    @Test
    void peekDoesNotMarkDirty() {
        FakeFiler filer = new FakeFiler(tempDir.resolve("does-not-exist.json"), tempDir);
        ProcessingEnvironment env = mockEnv(filer);

        FlamingockMetadataStore store = new FlamingockMetadataStore(env, new LoggerPreProcessor(env), METADATA_PATH, REFLECT_PATH);
        assertTrue(store.peek().isPresent());

        store.commit();
        assertFalse(Files.exists(tempDir.resolve(METADATA_PATH)),
                "peek must not mark store dirty");
    }

    private static ProcessingEnvironment mockEnv(Filer filer) {
        ProcessingEnvironment env = mock(ProcessingEnvironment.class);
        Messager messager = mock(Messager.class);
        when(env.getFiler()).thenReturn(filer);
        when(env.getMessager()).thenReturn(messager);
        Map<String, String> options = new HashMap<>();
        when(env.getOptions()).thenReturn(options);
        // Silence Messager
        lenient().doNothing().when(messager).printMessage(any(Diagnostic.Kind.class), anyString());
        return env;
    }

    private static PreviewPipeline samplePipeline() {
        CodePreviewChange c = new CodePreviewChange("id-1", null, "author",
                "com.example.changes.A", "com.example.changes.A",
                null, null, null, false, null, false, null, null, false);
        PreviewStage stage = new PreviewStage("default", StageType.DEFAULT, null,
                "com.example.changes", null, java.util.Collections.singletonList(c));
        return new PreviewPipeline(Arrays.asList(stage));
    }

    /**
     * Minimal Filer fake. {@code readPath} is what {@code getResource} reports for read attempts;
     * {@code writeRoot} is the base directory where {@code createResource} writes; the relative
     * name from {@code createResource} is appended to {@code writeRoot}.
     */
    private static final class FakeFiler implements Filer {
        private final Path readPath;
        private final Path writeRoot;

        FakeFiler(Path readPath) {
            this(readPath, null);
        }

        FakeFiler(Path readPath, Path writeRoot) {
            this.readPath = readPath;
            this.writeRoot = writeRoot;
        }

        @Override
        public javax.tools.JavaFileObject createSourceFile(CharSequence name, javax.lang.model.element.Element... originatingElements) {
            throw new UnsupportedOperationException();
        }

        @Override
        public javax.tools.JavaFileObject createClassFile(CharSequence name, javax.lang.model.element.Element... originatingElements) {
            throw new UnsupportedOperationException();
        }

        @Override
        public FileObject createResource(javax.tools.JavaFileManager.Location location, CharSequence pkg, CharSequence relativeName,
                                         javax.lang.model.element.Element... originatingElements) {
            if (writeRoot == null) {
                throw new UnsupportedOperationException("writeRoot not configured");
            }
            return new PathFileObject(writeRoot.resolve(relativeName.toString()), true);
        }

        @Override
        public FileObject getResource(javax.tools.JavaFileManager.Location location, CharSequence pkg, CharSequence relativeName) {
            if (eqLocation(location, StandardLocation.CLASS_OUTPUT)
                    && relativeName.toString().equals(METADATA_PATH)) {
                return new PathFileObject(readPath, false);
            }
            throw new IllegalArgumentException("Unsupported location: " + location
                    + " path: " + relativeName);
        }

        private static boolean eqLocation(javax.tools.JavaFileManager.Location a, javax.tools.JavaFileManager.Location b) {
            return a != null && b != null && a.getName().equals(b.getName());
        }
    }

    private static final class PathFileObject implements FileObject {
        private final Path path;
        private final boolean writable;

        PathFileObject(Path path, boolean writable) {
            this.path = path;
            this.writable = writable;
        }

        @Override
        public URI toUri() {
            return path.toUri();
        }

        @Override
        public String getName() {
            return path.getFileName().toString();
        }

        @Override
        public InputStream openInputStream() throws IOException {
            if (!Files.exists(path)) {
                throw new FileNotFoundException(path.toString());
            }
            return Files.newInputStream(path);
        }

        @Override
        public OutputStream openOutputStream() throws IOException {
            if (!writable) {
                throw new IOException("read-only");
            }
            Files.createDirectories(path.getParent());
            return Files.newOutputStream(path);
        }

        @Override
        public java.io.Reader openReader(boolean ignoreEncodingErrors) throws IOException {
            return new java.io.InputStreamReader(openInputStream(), java.nio.charset.StandardCharsets.UTF_8);
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
            return new String(Files.readAllBytes(path), java.nio.charset.StandardCharsets.UTF_8);
        }

        @Override
        public Writer openWriter() throws IOException {
            if (!writable) {
                throw new IOException("read-only");
            }
            Files.createDirectories(path.getParent());
            return new PrintWriter(Files.newBufferedWriter(path));
        }

        @Override
        public long getLastModified() {
            try {
                return Files.getLastModifiedTime(path).toMillis();
            } catch (IOException e) {
                return 0L;
            }
        }

        @Override
        public boolean delete() {
            try {
                return Files.deleteIfExists(path);
            } catch (IOException e) {
                return false;
            }
        }
    }
}
