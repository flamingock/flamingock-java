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

import io.flamingock.internal.common.core.preview.CodePreviewChange;
import io.flamingock.internal.common.core.preview.PreviewConstructor;
import io.flamingock.internal.common.core.preview.PreviewMethod;
import io.flamingock.internal.common.core.task.RecoveryDescriptor;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertNull;

class CodePreviewTaskBuilderTest {

    @Test
    void shouldBuildNullSourceFileForCodeChanges() {
        CodePreviewChange preview = CodePreviewTaskBuilder.instance()
                .setId("test-id")
                .setOrder("001")
                .setAuthor("author")
                .setSourceClassPath("io.flamingock.TestChange")
                .setConstructor(PreviewConstructor.getDefault())
                .setApplyMethod(new PreviewMethod("apply", Collections.emptyList()))
                .setRollbackMethod(null)
                .setRunAlways(false)
                .setTransactionalFlag(true)
                .setSystem(false)
                .setRecovery(RecoveryDescriptor.getDefault())
                .build();

        assertNull(preview.getSourceFile());
    }
}
