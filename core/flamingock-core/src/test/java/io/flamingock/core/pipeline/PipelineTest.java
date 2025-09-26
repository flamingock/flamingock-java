/*
 * Copyright 2023 Flamingock (https://www.flamingock.io)
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
package io.flamingock.core.pipeline;

import io.flamingock.common.test.cloud.deprecated.MockRunnerServerOld;
import io.flamingock.internal.common.core.error.FlamingockException;
import io.flamingock.internal.common.core.preview.CodePreviewChange;
import io.flamingock.internal.common.core.preview.PreviewMethod;
import io.flamingock.internal.common.core.preview.PreviewPipeline;
import io.flamingock.internal.common.core.preview.PreviewStage;
import io.flamingock.internal.common.core.task.RecoveryDescriptor;
import io.flamingock.api.StageType;
import io.flamingock.internal.core.pipeline.loaded.LoadedPipeline;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;


public class PipelineTest {
    private static MockRunnerServerOld mockRunnerServer;

    @Test
    @DisplayName("Should throw an exception when Pipeline.validateAndGetLoadedStages() if no stages")
    void shouldThrowExceptionWhenPipelineDoesNotContainStages() {


        LoadedPipeline emptyPipeline = LoadedPipeline.builder()
                .addPreviewPipeline(new PreviewPipeline())
                .build();

        FlamingockException exception = Assertions.assertThrows(FlamingockException.class, emptyPipeline::validate);

        Assertions.assertTrue(exception.getMessage().contains("Pipeline must contain at least one stage"), 
                "Error message should mention that pipeline must contain at least one stage");

    }

    @Test
    @DisplayName("Should throw an exception when the only stage is empty")
    void shouldThrowExceptionWhenTheOnlyStageEmpty() {

        PreviewPipeline previewPipeline = new PreviewPipeline();
        previewPipeline.setStages(Collections.singletonList(getPreviewStage("failing-stage-1")));

        LoadedPipeline pipeline = LoadedPipeline.builder()
                .addPreviewPipeline(previewPipeline)
                .build();

        FlamingockException exception = Assertions.assertThrows(FlamingockException.class, pipeline::validate);

        Assertions.assertTrue(exception.getMessage().contains("Stage[failing-stage-1] must contain at least one task"));

    }


    @Test
    @DisplayName("Should throw an exception when all stages are empty")
    void shouldThrowExceptionWhenAllStagesEmpty() {
        PreviewPipeline previewPipeline = new PreviewPipeline();
        previewPipeline.setStages(Arrays.asList(
                getPreviewStage("failing-stage-1"),
                getPreviewStage("failing-stage-2")));

        LoadedPipeline pipeline = LoadedPipeline.builder()
                .addPreviewPipeline(previewPipeline)
                .build();

        FlamingockException exception = Assertions.assertThrows(FlamingockException.class, pipeline::validate);

        Assertions.assertTrue(exception.getMessage().contains("Stage[failing-stage-1] must contain at least one task"));
        Assertions.assertTrue(exception.getMessage().contains("Stage[failing-stage-2] must contain at least one task"));

    }


    private static PreviewStage getPreviewStage(String name) {
        PreviewStage stage = Mockito.mock(PreviewStage.class);
        Mockito.when(stage.getType()).thenReturn(StageType.DEFAULT);
        Mockito.when(stage.getName()).thenReturn(name);
        Mockito.when(stage.getTasks()).thenReturn(Collections.emptyList());
        return stage;
    }

    @Test
    @DisplayName("Should throw an exception when a task has an invalid order format")
    void shouldThrowExceptionWhenTaskHasInvalidOrderFormat() {
        PreviewMethod executionMethod = new PreviewMethod("execute", Collections.emptyList());

        CodePreviewChange taskWithInvalidOrder1 = new CodePreviewChange(
                "task-with-invalid-order-1",
                "12", // Too short (only 2 alphanumeric characters)
                "test-author",
                PipelineTest.class.getName(),
                executionMethod,
                null,
                null,
                null,
                false,
                true,
                false,
                null,
                RecoveryDescriptor.getDefault());

        CodePreviewChange taskWithInvalidOrder2 = new CodePreviewChange(
                "task-with-invalid-order-2",
                "a_", // Only 1 alphanumeric character
                "test-author",
                PipelineTest.class.getName(),
                executionMethod,
                null,
                null,
                null,
                false,
                true,
                false,
                null,
                RecoveryDescriptor.getDefault());

        PreviewStage stage = Mockito.mock(PreviewStage.class);
        Mockito.when(stage.getType()).thenReturn(StageType.DEFAULT);
        Mockito.when(stage.getName()).thenReturn("stage-with-invalid-order-tasks");
        Mockito.when(stage.getTasks()).thenReturn((Collection) Arrays.asList(taskWithInvalidOrder1, taskWithInvalidOrder2));

        PreviewPipeline previewPipeline = new PreviewPipeline();
        previewPipeline.setStages(Collections.singletonList(stage));

        LoadedPipeline pipeline = LoadedPipeline.builder()
                .addPreviewPipeline(previewPipeline)
                .build();

        FlamingockException exception = Assertions.assertThrows(FlamingockException.class, pipeline::validate);
        Assertions.assertTrue(exception.getMessage().contains("Invalid order field format"), 
                "Error message should mention invalid order field format");
        Assertions.assertTrue(exception.getMessage().contains("task-with-invalid-order-1"), 
                "Error message should mention the task with invalid order");
    }

    @Test
    @DisplayName("Should validate successfully when tasks have valid order formats")
    void shouldValidateSuccessfullyWhenTasksHaveValidOrderFormats() {
        PreviewMethod executionMethod = new PreviewMethod("execute", Collections.emptyList());

        CodePreviewChange taskWithValidOrder1 = new CodePreviewChange(
                "task-with-valid-order-1",
                "001", // Valid 3 alphanumeric characters
                "test-author",
                PipelineTest.class.getName(),
                executionMethod,
                null,
                null,
                null,
                false,
                true,
                false,
                null,
                RecoveryDescriptor.getDefault());

        CodePreviewChange taskWithValidOrder2 = new CodePreviewChange(
                "task-with-valid-order-2",
                "abc", // Valid 3 alphanumeric characters
                "test-author",
                PipelineTest.class.getName(),
                executionMethod,
                null,
                null,
                null,
                false,
                true,
                false,
                null,
                RecoveryDescriptor.getDefault());

        CodePreviewChange taskWithValidOrder3 = new CodePreviewChange(
                "task-with-valid-order-3",
                "V1_2_3", // Valid with underscores and alphanumeric chars
                "test-author",
                PipelineTest.class.getName(),
                executionMethod,
                null,
                null,
                null,
                false,
                true,
                false,
                null,
                RecoveryDescriptor.getDefault());

        CodePreviewChange taskWithValidOrder4 = new CodePreviewChange(
                "task-with-valid-order-4",
                "20250925_01_migration", // Valid complex format
                "test-author",
                PipelineTest.class.getName(),
                executionMethod,
                null,
                null,
                null,
                false,
                true,
                false,
                null,
                RecoveryDescriptor.getDefault());

        PreviewStage stage = Mockito.mock(PreviewStage.class);
        Mockito.when(stage.getType()).thenReturn(StageType.DEFAULT);
        Mockito.when(stage.getName()).thenReturn("stage-with-valid-order-tasks");
        Mockito.when(stage.getTasks()).thenReturn((Collection) Arrays.asList(
                taskWithValidOrder1, taskWithValidOrder2, taskWithValidOrder3, taskWithValidOrder4));

        PreviewPipeline previewPipeline = new PreviewPipeline();
        previewPipeline.setStages(Collections.singletonList(stage));

        LoadedPipeline pipeline = LoadedPipeline.builder()
                .addPreviewPipeline(previewPipeline)
                .build();

        Assertions.assertDoesNotThrow(pipeline::validate);
    }

    @Test
    @DisplayName("Should throw an exception when there are duplicate Change IDs across stages")
    void shouldThrowExceptionWhenDuplicateChangeIds() {
        // Create a preview method for execution
        PreviewMethod executionMethod = new PreviewMethod("execute", Collections.emptyList());

        CodePreviewChange task1 = new CodePreviewChange(
                "duplicate-id",
                "001", // Valid: 3 alphanumeric characters
                "test-author",
                PipelineTest.class.getName(),
                executionMethod,
                null,
                null,
                null,
                false,
                true,
                false,
                null,
                RecoveryDescriptor.getDefault());

        CodePreviewChange task2 = new CodePreviewChange(
                "unique-id",
                "002", // Valid: 3 alphanumeric characters
                "test-author",
                PipelineTest.class.getName(),
                executionMethod,
                null,
                null,
                null,
                false,
                true,
                false,
                null,
                RecoveryDescriptor.getDefault());

        CodePreviewChange task3 = new CodePreviewChange(
                "duplicate-id",
                "003", // Valid: 3 alphanumeric characters
                "test-author",
                PipelineTest.class.getName(),
                executionMethod,
                null,
                null,
                null,
                false,
                true,
                false,
                null,
                RecoveryDescriptor.getDefault());

        PreviewStage stage1 = Mockito.mock(PreviewStage.class);
        Mockito.when(stage1.getType()).thenReturn(StageType.DEFAULT);
        Mockito.when(stage1.getName()).thenReturn("stage1");
        Mockito.when(stage1.getTasks()).thenReturn((Collection) Arrays.asList(task1, task2));

        PreviewStage stage2 = Mockito.mock(PreviewStage.class);
        Mockito.when(stage2.getType()).thenReturn(StageType.DEFAULT);
        Mockito.when(stage2.getName()).thenReturn("stage2");
        Mockito.when(stage2.getTasks()).thenReturn((Collection) Collections.singletonList(task3));

        PreviewPipeline previewPipeline = new PreviewPipeline();
        previewPipeline.setStages(Arrays.asList(stage1, stage2));

        LoadedPipeline pipeline = LoadedPipeline.builder()
                .addPreviewPipeline(previewPipeline)
                .build();

        FlamingockException exception = Assertions.assertThrows(FlamingockException.class, pipeline::validate);
        Assertions.assertTrue(exception.getMessage().contains("Duplicate change IDs found across stages"));
        Assertions.assertTrue(exception.getMessage().contains("Duplicate change IDs found across stages: duplicate-id"));
    }

}
