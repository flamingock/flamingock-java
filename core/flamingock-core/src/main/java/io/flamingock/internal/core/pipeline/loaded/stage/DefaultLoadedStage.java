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

package io.flamingock.internal.core.pipeline.loaded.stage;


import io.flamingock.internal.common.core.preview.StageType;
import io.flamingock.internal.core.task.loaded.AbstractLoadedTask;

import java.util.Collection;

import static io.flamingock.internal.core.pipeline.loaded.stage.StageValidationContext.SortType.SEQUENTIAL_FORMATTED;

/**
 * It's the result of adding the loaded task to the ProcessDefinition
 */
public class DefaultLoadedStage extends AbstractLoadedStage {

    private static final StageValidationContext validationContext = StageValidationContext.builder()
            .setSorted(SEQUENTIAL_FORMATTED)
            .build();

    public DefaultLoadedStage(String name,
                              StageType type,
                              Collection<AbstractLoadedTask> loadedTasks,
                              boolean parallel) {
        super(name, type, loadedTasks, parallel, validationContext);

    }



}
