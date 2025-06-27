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

package io.flamingock.cloud.transaction.mongodb.sync;

import io.flamingock.api.StageType;
import io.flamingock.internal.util.Pair;
import io.flamingock.internal.util.Trio;
import io.flamingock.api.annotations.ChangeUnit;
import io.flamingock.internal.common.core.preview.CodePreviewChangeUnit;
import io.flamingock.internal.common.core.preview.PreviewMethod;
import io.flamingock.internal.common.core.preview.PreviewPipeline;
import io.flamingock.internal.common.core.preview.PreviewStage;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public class PipelineTestHelper {

    private static final Function<Class<?>, Trio<String, String, Boolean>> infoExtractor = c -> {
        ChangeUnit ann = c.getAnnotation(ChangeUnit.class);
        return new Trio<>(ann.id(), ann.order(), ann.transactional());
    };

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
     *   <li>The first item is the {@link Class} annotated with {@link ChangeUnit} or {@link io.mongock.api.annotations.ChangeUnit}</li>
     *   <li>The second item is a {@link List} of parameter types (as {@link Class}) expected by the method annotated with {@code @Execution}</li>
     *   <li>The third item is a {@link List} of parameter types (as {@link Class}) expected by the method annotated with {@code @RollbackExecution}</li>
     * </ul>
     *
     * @param changeDefinitions varargs of pairs containing change classes and their execution method parameters
     * @return a {@link PreviewPipeline} ready for preview or testing
     */
    @SafeVarargs
    public static PreviewPipeline getPreviewPipeline(String stageName, Trio<Class<?>, List<Class<?>>, List<Class<?>>>... changeDefinitions) {

        List<CodePreviewChangeUnit> tasks = Arrays.stream(changeDefinitions)
                .map(trio -> {
                    Function<Class<?>, Trio<String, String, Boolean>> extractor = infoExtractor;
                    Trio<String, String, Boolean> changeInfo = extractor.apply(trio.getFirst());
                    PreviewMethod rollback = null;
                    PreviewMethod rollbackBeforeExecution = null;
                    if (trio.getThird() != null) {
                        rollback = new PreviewMethod("rollbackExecution", getParameterTypes(trio.getThird()));
                        rollbackBeforeExecution = new PreviewMethod("rollbackBeforeExecution", getParameterTypes(trio.getThird()));
                    }

                    List<CodePreviewChangeUnit> changes = new ArrayList<>();
                    changes.add(new CodePreviewChangeUnit(
                            changeInfo.getFirst(),
                            changeInfo.getSecond(),
                            trio.getFirst().getName(),
                            new PreviewMethod("execution", getParameterTypes(trio.getSecond())),
                            rollback,
                            new PreviewMethod("beforeExecution", getParameterTypes(trio.getSecond())),
                            rollbackBeforeExecution,
                            false,
                            changeInfo.getThird(),
                            false
                    ));
                    return changes;
                })
                .flatMap(List::stream)
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

    @SafeVarargs
    public static PreviewPipeline getPreviewPipeline(Trio<Class<?>, List<Class<?>>, List<Class<?>>>... changeDefinitions) {
        return getPreviewPipeline("default-stage-name", changeDefinitions);
    }
}
