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
import io.flamingock.core.api.exception.FlamingockException;
import io.flamingock.core.preview.PreviewPipeline;
import io.flamingock.core.preview.PreviewStage;
import io.flamingock.internal.core.pipeline.Pipeline;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Arrays;
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

        Assertions.assertEquals("Pipeline must contain at least one stage", exception.getMessage());

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

        Assertions.assertEquals("There are empty stages: failing-stage-1", exception.getMessage());
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

        Assertions.assertEquals("There are empty stages: failing-stage-1,failing-stage-2", exception.getMessage());
    }


    private static PreviewStage getPreviewStage(String name) {
        PreviewStage stage = Mockito.mock(PreviewStage.class);
        Mockito.when(stage.getName()).thenReturn(name);
        Mockito.when(stage.getTasks()).thenReturn(Collections.emptyList());
        return stage;
    }

}
