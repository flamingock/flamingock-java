/*
 * Copyright 2023 Flamingock ("https://oss.flamingock.io")
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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
import io.flamingock.internal.common.core.preview.CodePreviewChangeUnit;
import io.flamingock.internal.common.core.preview.PreviewMethod;
import io.flamingock.internal.common.core.preview.PreviewPipeline;
import io.flamingock.internal.common.core.preview.PreviewStage;
import io.flamingock.internal.core.pipeline.Pipeline;
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


        Pipeline emptyPipeline = Pipeline.builder()
                .addPreviewPipeline(new PreviewPipeline())
                .build();

        FlamingockException exception = Assertions.assertThrows(FlamingockException.class, emptyPipeline::validateAndGetLoadedStages);

        Assertions.assertTrue(exception.getMessage().contains("Pipeline must contain at least one stage"), 
                "Error message should mention that pipeline must contain at least one stage");

    }

    @Test
    @DisplayName("Should throw an exception when the only stage is empty")
    void shouldThrowExceptionWhenTheOnlyStageEmpty() {

        PreviewPipeline previewPipeline = new PreviewPipeline();
        previewPipeline.setStages(Collections.singletonList(getPreviewStage("failing-stage-1")));

        Pipeline pipeline = Pipeline.builder()
                .addPreviewPipeline(previewPipeline)
                .build();

        FlamingockException exception = Assertions.assertThrows(FlamingockException.class, pipeline::validateAndGetLoadedStages);

        Assertions.assertTrue(exception.getMessage().contains("Stage[failing-stage-1] must contain at least one task"));

    }


    @Test
    @DisplayName("Should throw an exception when all stages are empty")
    void shouldThrowExceptionWhenAllStagesEmpty() {
        PreviewPipeline previewPipeline = new PreviewPipeline();
        previewPipeline.setStages(Arrays.asList(
                getPreviewStage("failing-stage-1"),
                getPreviewStage("failing-stage-2")));

        Pipeline pipeline = Pipeline.builder()
                .addPreviewPipeline(previewPipeline)
                .build();

        FlamingockException exception = Assertions.assertThrows(FlamingockException.class, pipeline::validateAndGetLoadedStages);

        Assertions.assertTrue(exception.getMessage().contains("Stage[failing-stage-1] must contain at least one task"));
        Assertions.assertTrue(exception.getMessage().contains("Stage[failing-stage-2] must contain at least one task"));

    }


    private static PreviewStage getPreviewStage(String name) {
        PreviewStage stage = Mockito.mock(PreviewStage.class);
        Mockito.when(stage.getName()).thenReturn(name);
        Mockito.when(stage.getTasks()).thenReturn(Collections.emptyList());
        return stage;
    }

    @Test
    @DisplayName("Should throw an exception when a task has an invalid order format")
    void shouldThrowExceptionWhenTaskHasInvalidOrderFormat() {
        PreviewMethod executionMethod = new PreviewMethod("execute", Collections.emptyList());

        CodePreviewChangeUnit taskWithInvalidOrder1 = new CodePreviewChangeUnit(
                "task-with-invalid-order-1",
                "12", // Too short (only 2 digits)
                PipelineTest.class.getName(),
                executionMethod,
                null,
                null,
                null,
                false,
                true,
                false);

        CodePreviewChangeUnit taskWithInvalidOrder2 = new CodePreviewChangeUnit(
                "task-with-invalid-order-3",
                "abc", // Non-numeric
                PipelineTest.class.getName(),
                executionMethod,
                null,
                null,
                null,
                false,
                true,
                false);

        PreviewStage stage = Mockito.mock(PreviewStage.class);
        Mockito.when(stage.getName()).thenReturn("stage-with-invalid-order-tasks");
        Mockito.when(stage.getTasks()).thenReturn((Collection) Arrays.asList(taskWithInvalidOrder1, taskWithInvalidOrder2));

        PreviewPipeline previewPipeline = new PreviewPipeline();
        previewPipeline.setStages(Collections.singletonList(stage));

        Pipeline pipeline = Pipeline.builder()
                .addPreviewPipeline(previewPipeline)
                .build();

        FlamingockException exception = Assertions.assertThrows(FlamingockException.class, pipeline::validateAndGetLoadedStages);
        Assertions.assertTrue(exception.getMessage().contains("Invalid order field format"), 
                "Error message should mention invalid order field format");
        Assertions.assertTrue(exception.getMessage().contains("task-with-invalid-order-1"), 
                "Error message should mention the task with invalid order");
    }

    @Test
    @DisplayName("Should validate successfully when tasks have valid order formats")
    void shouldValidateSuccessfullyWhenTasksHaveValidOrderFormats() {
        PreviewMethod executionMethod = new PreviewMethod("execute", Collections.emptyList());

        CodePreviewChangeUnit taskWithValidOrder1 = new CodePreviewChangeUnit(
                "task-with-valid-order-1",
                "001", // Valid 3-digit format
                PipelineTest.class.getName(),
                executionMethod,
                null,
                null,
                null,
                false,
                true,
                false);

        CodePreviewChangeUnit taskWithValidOrder2 = new CodePreviewChangeUnit(
                "task-with-valid-order-2",
                "999", // Valid 3-digit format
                PipelineTest.class.getName(),
                executionMethod,
                null,
                null,
                null,
                false,
                true,
                false);

        CodePreviewChangeUnit taskWithValidOrder3 = new CodePreviewChangeUnit(
                "task-with-valid-order-3",
                "0010", // Valid 4-digit format
                PipelineTest.class.getName(),
                executionMethod,
                null,
                null,
                null,
                false,
                true,
                false);

        CodePreviewChangeUnit taskWithValidOrder4 = new CodePreviewChangeUnit(
                "task-with-valid-order-4",
                "9999", // Valid 4-digit format
                PipelineTest.class.getName(),
                executionMethod,
                null,
                null,
                null,
                false,
                true,
                false);

        PreviewStage stage = Mockito.mock(PreviewStage.class);
        Mockito.when(stage.getName()).thenReturn("stage-with-valid-order-tasks");
        Mockito.when(stage.getTasks()).thenReturn((Collection) Arrays.asList(
                taskWithValidOrder1, taskWithValidOrder2, taskWithValidOrder3, taskWithValidOrder4));

        PreviewPipeline previewPipeline = new PreviewPipeline();
        previewPipeline.setStages(Collections.singletonList(stage));

        Pipeline pipeline = Pipeline.builder()
                .addPreviewPipeline(previewPipeline)
                .build();

        Assertions.assertDoesNotThrow(pipeline::validateAndGetLoadedStages);
    }

    @Test
    @DisplayName("Should throw an exception when there are duplicate ChangeUnit IDs across stages")
    void shouldThrowExceptionWhenDuplicateChangeUnitIds() {
        // Create a preview method for execution
        PreviewMethod executionMethod = new PreviewMethod("execute", Collections.emptyList());

        CodePreviewChangeUnit task1 = new CodePreviewChangeUnit(
                "duplicate-id",
                "001",
                PipelineTest.class.getName(),
                executionMethod,
                null,
                null,
                null,
                false,
                true,
                false);

        CodePreviewChangeUnit task2 = new CodePreviewChangeUnit(
                "unique-id",
                "002",
                PipelineTest.class.getName(),
                executionMethod,
                null,
                null,
                null,
                false,
                true,
                false);

        CodePreviewChangeUnit task3 = new CodePreviewChangeUnit(
                "duplicate-id",
                "003",
                PipelineTest.class.getName(),
                executionMethod,
                null,
                null,
                null,
                false,
                true,
                false);

        PreviewStage stage1 = Mockito.mock(PreviewStage.class);
        Mockito.when(stage1.getName()).thenReturn("stage1");
        Mockito.when(stage1.getTasks()).thenReturn((Collection) Arrays.asList(task1, task2));

        PreviewStage stage2 = Mockito.mock(PreviewStage.class);
        Mockito.when(stage2.getName()).thenReturn("stage2");
        Mockito.when(stage2.getTasks()).thenReturn((Collection) Collections.singletonList(task3));

        PreviewPipeline previewPipeline = new PreviewPipeline();
        previewPipeline.setStages(Arrays.asList(stage1, stage2));

        Pipeline pipeline = Pipeline.builder()
                .addPreviewPipeline(previewPipeline)
                .build();

        FlamingockException exception = Assertions.assertThrows(FlamingockException.class, pipeline::validateAndGetLoadedStages);
        Assertions.assertTrue(exception.getMessage().contains("Duplicate changeUnit IDs found across stages"));
        Assertions.assertTrue(exception.getMessage().contains("Duplicate changeUnit IDs found across stages: duplicate-id"));
    }

}
