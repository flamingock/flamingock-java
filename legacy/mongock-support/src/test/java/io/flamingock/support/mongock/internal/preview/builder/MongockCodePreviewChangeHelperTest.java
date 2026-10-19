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
package io.flamingock.support.mongock.internal.preview.builder;

import io.flamingock.internal.common.core.preview.CodePreviewChange;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import org.junit.jupiter.api.Test;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MongockCodePreviewChangeHelperTest {

    @Test
    void mapsRunAlwaysForChangeUnitAndChangeSetWithoutRejectingMixedLegacyChanges() {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertTrue(compiler != null, "Tests require a JDK with the Java compiler");

        CapturingProcessor processor = new CapturingProcessor();
        JavaFileObject source = new StringJavaFileObject("LegacyChanges", ""
                + "import com.github.cloudyrock.mongock.ChangeLog;\n"
                + "import com.github.cloudyrock.mongock.ChangeSet;\n"
                + "import io.mongock.api.annotations.ChangeUnit;\n"
                + "import io.mongock.api.annotations.Execution;\n"
                + "\n"
                + "@ChangeUnit(id = \"unit\", order = \"001\", author = \"author\", runAlways = true)\n"
                + "class UnitChange {\n"
                + "  @Execution public void execute() {}\n"
                + "}\n"
                + "\n"
                + "@ChangeLog\n"
                + "class SetChanges {\n"
                + "  @ChangeSet(id = \"always\", order = \"002\", author = \"author\", runAlways = true)\n"
                + "  public void always() {}\n"
                + "  @ChangeSet(id = \"normal\", order = \"003\", author = \"author\")\n"
                + "  public void normal() {}\n"
                + "}\n");

        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null)) {
            Path output = Paths.get("build/tmp/mongock-run-always-test");
            Files.createDirectories(output);
            fileManager.setLocation(StandardLocation.CLASS_OUTPUT,
                    Collections.singletonList(output.toFile()));
            JavaCompiler.CompilationTask task = compiler.getTask(
                    null,
                    fileManager,
                    null,
                    Arrays.asList("-proc:only"),
                    null,
                    Collections.singletonList(source));
            task.setProcessors(Collections.singletonList(processor));
            assertTrue(task.call());
        } catch (Exception e) {
            throw new AssertionError("Could not compile Mongock fixtures", e);
        }

        Map<String, CodePreviewChange> changes = processor.changes.stream()
                .collect(Collectors.toMap(CodePreviewChange::getId, Function.identity()));
        assertEquals(3, changes.size());
        assertTrue(changes.get("unit").isRunAlways());
        assertTrue(changes.get("always").isRunAlways());
        assertTrue(!changes.get("normal").isRunAlways());
    }

    private static final class CapturingProcessor extends AbstractProcessor {
        private final List<CodePreviewChange> changes = new ArrayList<>();

        @Override
        public Set<String> getSupportedAnnotationTypes() {
            return new java.util.HashSet<>(Arrays.asList(
                    ChangeUnit.class.getCanonicalName(),
                    "com.github.cloudyrock.mongock.ChangeLog"));
        }

        @Override
        public SourceVersion getSupportedSourceVersion() {
            return SourceVersion.latestSupported();
        }

        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnvironment) {
            if (roundEnvironment.processingOver()) {
                return false;
            }
            MongockCodePreviewChangeHelper helper = new MongockCodePreviewChangeHelper();
            for (javax.lang.model.element.Element element : roundEnvironment.getRootElements()) {
                if (element instanceof TypeElement) {
                    TypeElement type = (TypeElement) element;
                    if (type.getAnnotation(ChangeUnit.class) != null
                            || type.getAnnotation(com.github.cloudyrock.mongock.ChangeLog.class) != null) {
                        changes.addAll(helper.getCodePreviewChanges(type, "test-target"));
                    }
                }
            }
            return false;
        }
    }

    private static final class StringJavaFileObject extends javax.tools.SimpleJavaFileObject {
        private final String source;

        private StringJavaFileObject(String className, String source) {
            super(URI.create("string:///" + className + JavaFileObject.Kind.SOURCE.extension),
                    JavaFileObject.Kind.SOURCE);
            this.source = source;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return source;
        }
    }
}
