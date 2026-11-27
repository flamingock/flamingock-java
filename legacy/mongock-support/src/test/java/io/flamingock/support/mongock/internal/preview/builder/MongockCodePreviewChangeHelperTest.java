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

import io.flamingock.common.test.processor.AnnotatedElementProcessor;
import io.flamingock.common.test.processor.JavacHelper;
import io.flamingock.internal.common.core.preview.CodePreviewChange;
import io.mongock.api.annotations.ChangeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static io.flamingock.common.test.processor.JavacHelper.getSourcePath;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class MongockCodePreviewChangeHelperTest {

    private final MongockCodePreviewChangeHelper mongockCodePreviewChangeHelper = new MongockCodePreviewChangeHelper();
    private final JavacHelper javacHelper = new JavacHelper();

    @Test
    @DisplayName("inherits execution and rollback methods from its template superclass")
    void inheritsExecutionAndRollbackMethodsFromTemplateSuperclass() throws IOException, URISyntaxException {
        // Arrange
        Path sourcePath = getSourcePath(getClass(), "/changes/ChangeUnitUnderTest.java");
        String expectedRollbackPreviewMethodName = "rollback";
        String expectedExecutePreviewMethodName = "execute";
        List<List<CodePreviewChange>> actualChanges = Collections.synchronizedList(new ArrayList<>());

        // Act
        javacHelper.compile(
                sourcePath,
                new AnnotatedElementProcessor(ChangeUnit.class, element -> {
                    List<CodePreviewChange> changes = mongockCodePreviewChangeHelper.getCodePreviewChanges(element, "mongock");
                    actualChanges.add(changes);
                })
        );

        // Assert
        assertEquals(1, actualChanges.size());
        List<CodePreviewChange> changes = actualChanges.get(0);

        assertEquals(1, changes.size());
        CodePreviewChange change = changes.get(0);

        assertNotNull(change.getApplyPreviewMethod());
        String actualExecutePreviewMethodName = change.getApplyPreviewMethod().getName();
        assertEquals(expectedExecutePreviewMethodName, actualExecutePreviewMethodName);

        assertNotNull(change.getRollbackPreviewMethod());
        String actualRollbackPreviewMethodName = change.getRollbackPreviewMethod().getName();
        assertEquals(expectedRollbackPreviewMethodName, actualRollbackPreviewMethodName);
    }
}
