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
package io.flamingock.common.test.pipeline;

import io.flamingock.api.StageType;
import io.flamingock.core.processor.util.Deserializer;
import io.flamingock.internal.common.core.preview.AbstractPreviewTask;
import io.flamingock.internal.common.core.preview.PreviewPipeline;
import io.flamingock.internal.common.core.preview.PreviewStage;
import org.jetbrains.annotations.NotNull;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class PipelineTestHelper {


    @NotNull
    private static List<String> getParameterTypes(List<Class<?>> second) {
        return second
                .stream()
                .map(Class::getName)
                .collect(Collectors.toList());
    }

    public static void testWithMockedPipeline(List<ChangeUnitTestDefinition> changeUnitTestDefinitions,
                      Runnable testOperation) {
        try (MockedStatic<Deserializer> mocked = Mockito.mockStatic(Deserializer.class)) {
            mocked.when(Deserializer::readPreviewPipelineFromFile).thenReturn(
                    PipelineTestHelper.getPreviewPipeline(changeUnitTestDefinitions)
            );
            testOperation.run();
        }
    }


    public static PreviewPipeline getPreviewPipeline(String stageName, List<ChangeUnitTestDefinition> changeDefinitions) {

        List<AbstractPreviewTask> tasks = changeDefinitions.stream()
                .map(ChangeUnitTestDefinition::toPreview)
                .collect(Collectors.toList());

        PreviewStage stage = new PreviewStage(
                stageName,
                StageType.DEFAULT,
                "some description",
                null,
                null,
                tasks
        );

        return new PreviewPipeline(Collections.singletonList(stage));
    }

    public static PreviewPipeline getPreviewPipeline(String stageName, ChangeUnitTestDefinition... changeDefinitions) {
        return getPreviewPipeline(stageName, Arrays.asList(changeDefinitions));
    }

    public static PreviewPipeline getPreviewPipeline(ChangeUnitTestDefinition... changeDefinitions) {
        return getPreviewPipeline(Arrays.asList(changeDefinitions));
    }

    public static PreviewPipeline getPreviewPipeline(List<ChangeUnitTestDefinition> changeDefinitions) {
        return getPreviewPipeline("default-stage-name", changeDefinitions);
    }
}
