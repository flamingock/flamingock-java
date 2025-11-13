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
package io.flamingock.community.couchbase;

import io.flamingock.api.StageType;
import io.flamingock.api.annotations.TargetSystem;
import io.flamingock.internal.common.core.preview.PreviewConstructor;
import io.flamingock.internal.common.core.task.RecoveryDescriptor;
import io.flamingock.internal.common.core.task.TargetSystemDescriptor;
import io.flamingock.internal.core.task.loaded.ChangeOrderUtil;
import io.flamingock.internal.util.Pair;
import io.flamingock.internal.util.Trio;
import io.flamingock.api.annotations.Change;
import io.flamingock.internal.common.core.preview.CodePreviewChange;
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

    private static final Function<Class<?>, ChangeInfo> infoExtractor = c -> {
        Change ann = c.getAnnotation(Change.class);
        TargetSystem targetSystemAnn = c.getAnnotation(TargetSystem.class);
        String targetSystemId = targetSystemAnn != null ? targetSystemAnn.id() : null;
        String changeId = ann.id();
        String order = ChangeOrderUtil.getMatchedOrderFromClassName(changeId, null, c.getName());
        return new ChangeInfo(changeId, order, ann.author(), targetSystemId, ann.transactional());
    };

    @NotNull
    private static List<String> getParameterTypes(List<Class<?>> second) {
        return second
                .stream()
                .map(Class::getName)
                .collect(Collectors.toList());
    }

    /**
     * Builds a {@link PreviewPipeline} composed of a single {@link PreviewStage} containing one or more {@link CodePreviewChange}s.
     * <p>
     * Each change is derived from a {@link Pair} where:
     * <ul>
     *   <li>The first item is the {@link Class} annotated with {@link Change}</li>
     *   <li>The second item is a {@link List} of parameter types (as {@link Class}) expected by the method annotated with {@code @Apply}</li>
     *   <li>The third item is a {@link List} of parameter types (as {@link Class}) expected by the method annotated with {@code @Rollback}</li>
     * </ul>
     *
     * @param changeDefinitions varargs of pairs containing change classes and their execution method parameters
     * @return a {@link PreviewPipeline} ready for preview or testing
     */
    @SafeVarargs
    public static PreviewPipeline getPreviewPipeline(String stageName, Trio<Class<?>, List<Class<?>>, List<Class<?>>>... changeDefinitions) {

        List<CodePreviewChange> tasks = Arrays.stream(changeDefinitions)
                .map(trio -> {
                    ChangeInfo changeInfo = infoExtractor.apply(trio.getFirst());
                    PreviewMethod rollback = null;
                    if (trio.getThird() != null) {
                        rollback = new PreviewMethod("rollback", getParameterTypes(trio.getThird()));
                    }

                    List<CodePreviewChange> changes = new ArrayList<>();
                    changes.add(new CodePreviewChange(
                            changeInfo.getChangeId(),
                            changeInfo.getOrder(),
                            changeInfo.getAuthor(),
                            trio.getFirst().getName(),
                            PreviewConstructor.getDefault(),
                            new PreviewMethod("apply", getParameterTypes(trio.getSecond())),
                            rollback,
                            false,
                            changeInfo.transactional,
                            false,
                            changeInfo.targetSystem,
                            RecoveryDescriptor.getDefault(),
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
                tasks
        );

        return new PreviewPipeline(Collections.singletonList(stage));
    }

    @SafeVarargs
    public static PreviewPipeline getPreviewPipeline(Trio<Class<?>, List<Class<?>>, List<Class<?>>>... changeDefinitions) {
        return getPreviewPipeline("default-stage-name", changeDefinitions);
    }



    static class ChangeInfo {
        private final String changeId;
        private final String order;
        private final String author;
        private final TargetSystemDescriptor targetSystem;
        private final boolean transactional;

        public ChangeInfo(String changeId, String order, String author, String targetSystemId, boolean transactional) {
            this.changeId = changeId;
            this.order = order;
            this.author = author;
            this.targetSystem = new TargetSystemDescriptor(targetSystemId);
            this.transactional = transactional;
        }

        public String getChangeId() {
            return changeId;
        }

        public String getOrder() {
            return order;
        }

        public String getAuthor() {
            return author;
        }

        public TargetSystemDescriptor getTargetSystem() {
            return targetSystem;
        }

        public boolean isTransactional() {
            return transactional;
        }
    }

}
