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
package io.flamingock.internal.common.core.preview.builder;

import io.flamingock.internal.common.core.preview.TemplatePreviewChange;
import io.flamingock.internal.common.core.change.TargetSystemDescriptor;
import io.flamingock.internal.common.core.template.ChangeTemplateFileContent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TemplatePreviewChangeBuilder transactional nullable support")
class TemplatePreviewChangeBuilderTest {

    @Test
    @DisplayName("Should preserve null transactional from YAML")
    void shouldPreserveNullTransactionalFromYaml() {
        ChangeTemplateFileContent content = new ChangeTemplateFileContent(
                "test-id", "author", "SqlTemplate", null,
                null, null, "CREATE TABLE", null,
                TargetSystemDescriptor.fromId("postgresql"), null);

        TemplatePreviewChange preview = TemplatePreviewChangeBuilder.builder(content)
                .setFileName("_0001__test.yaml")
                .build();

        assertEquals(Optional.empty(), preview.getTransactionalFlag());
        assertEquals("_0001__test.yaml", preview.getSourceFile());
    }

    @Test
    @DisplayName("Should preserve explicit true transactional from YAML")
    void shouldPreserveExplicitTrueTransactional() {
        ChangeTemplateFileContent content = new ChangeTemplateFileContent(
                "test-id", "author", "SqlTemplate", null,
                true, null, "CREATE TABLE", null,
                TargetSystemDescriptor.fromId("postgresql"), null);

        TemplatePreviewChange preview = TemplatePreviewChangeBuilder.builder(content)
                .setFileName("_0001__test.yaml")
                .build();

        assertEquals(Optional.of(true), preview.getTransactionalFlag());
        assertEquals("_0001__test.yaml", preview.getSourceFile());
    }

    @Test
    @DisplayName("Should preserve explicit false transactional from YAML")
    void shouldPreserveExplicitFalseTransactional() {
        ChangeTemplateFileContent content = new ChangeTemplateFileContent(
                "test-id", "author", "SqlTemplate", null,
                false, null, "CREATE TABLE", null,
                TargetSystemDescriptor.fromId("postgresql"), null);

        TemplatePreviewChange preview = TemplatePreviewChangeBuilder.builder(content)
                .setFileName("_0001__test.yaml")
                .build();

        assertEquals(Optional.of(false), preview.getTransactionalFlag());
        assertEquals("_0001__test.yaml", preview.getSourceFile());
    }
}
