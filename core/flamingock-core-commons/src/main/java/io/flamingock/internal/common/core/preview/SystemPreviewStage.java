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

package io.flamingock.internal.common.core.preview;

import io.flamingock.api.StageType;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * This class represents the process defined by the user in the builder, yaml, etc.
 * It doesn't necessary contain directly the tasks, it can contain the code packages, etc.
 */

public class SystemPreviewStage extends PreviewStage {


    public SystemPreviewStage() {
    }

    //TODO it shouldn't be public
    public SystemPreviewStage(String name,
                              String description,
                              String sourcesPackage,
                              String resourcesDir,
                              Collection<? extends AbstractPreviewTask> tasks,
                              boolean parallel) {
        super(name,
                StageType.SYSTEM,
                description,
                sourcesPackage,
                resourcesDir, tasks, parallel);

    }



    public static class SystemBuilder extends AbstractBuilder<SystemPreviewStage> {



        @NotNull
        @Override
        protected SystemPreviewStage buildInstance(String name,
                                             String description,
                                             String sourcesPackage,
                                             String resourcesDir,
                                             Collection<AbstractPreviewTask> allDescriptors,
                                             boolean parallel) {
            return new SystemPreviewStage(name, description, sourcesPackage, resourcesDir, allDescriptors, parallel);
        }
    }



}
