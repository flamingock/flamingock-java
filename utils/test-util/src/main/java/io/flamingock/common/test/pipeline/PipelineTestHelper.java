/*
 * Copyright 2023 Flamingock (https://oss.flamingock.io)
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
import io.flamingock.internal.util.Pair;
import io.flamingock.api.annotations.ChangeUnit;
import io.flamingock.internal.common.core.preview.AbstractPreviewTask;
import io.flamingock.internal.common.core.preview.CodePreviewChangeUnit;
import io.flamingock.internal.common.core.preview.PreviewPipeline;
import io.flamingock.internal.common.core.preview.PreviewStage;
import org.jetbrains.annotations.NotNull;

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

    /**
     * Builds a {@link PreviewPipeline} composed of a single {@link PreviewStage} containing one or more {@link CodePreviewChangeUnit}s.
     * <p>
     * Each change unit is derived from a {@link Pair} where:
     * <ul>
     *   <li>The first item is the {@link Class} annotated with {@link ChangeUnit}</li>
     *   <li>The second item is a {@link List} of parameter types (as {@link Class}) expected by the method annotated with {@code @Execution}</li>
     *   <li>The third item is a {@link List} of parameter types (as {@link Class}) expected by the method annotated with {@code @RollbackExecution}</li>
     * </ul>
     *
     * @param changeDefinitions varargs of pairs containing change classes and their execution method parameters
     * @return a {@link PreviewPipeline} ready for preview or testing
     */
    public static PreviewPipeline getPreviewPipeline(String stageName, ChangeUnitTestDefinition... changeDefinitions) {

        List<AbstractPreviewTask> tasks = Arrays.stream(changeDefinitions)
                .map(ChangeUnitTestDefinition::toPreview)
                .collect(Collectors.toList());

        PreviewStage stage = new PreviewStage(
                stageName,
                StageType.DEFAULT,
                "some description",
                null,
                null,
                tasks,
                false
        );

        return new PreviewPipeline(Collections.singletonList(stage));
    }

    public static PreviewPipeline getPreviewPipeline(ChangeUnitTestDefinition... changeDefinitions) {
        return getPreviewPipeline("default-stage-name", changeDefinitions);
    }
}
