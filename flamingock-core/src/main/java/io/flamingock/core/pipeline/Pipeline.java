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

package io.flamingock.core.pipeline;

import io.flamingock.core.task.filter.TaskFilter;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class Pipeline {

    public static PipelineBuilder builder() {
        return new PipelineBuilder();
    }
    private final List<Stage> stages;

    private Pipeline(List< Stage> stages) {
        this.stages = stages;
    }

    public List<LoadedStage> getLoadedStages() {
        return stages
                .stream()
                .map(Stage::load)
                .collect(Collectors.toList());
    }


    public static class PipelineBuilder {

        private final Collection<Stage> stages ;
        private final Collection<TaskFilter> taskFilters;

        private PipelineBuilder() {
            stages = new LinkedHashSet<>();
            taskFilters = new HashSet<>();
        }

        public PipelineBuilder addStages(Collection<Stage> stages) {
            this.stages.addAll(stages);
            return this;
        }

        public PipelineBuilder addStage(Stage stage) {
            stages.add(stage);
            return this;
        }

        public PipelineBuilder setFilters(Collection<TaskFilter> taskFilters) {
            this.taskFilters.addAll(taskFilters);
            return this;
        }

        public Pipeline build() {
            List<Stage> stagesWithTaskFilter = stages.stream()
                    .map(stage -> stage.addFilters(taskFilters))
                    .collect(Collectors.toList());
            return new Pipeline(stagesWithTaskFilter);
        }

    }
}
