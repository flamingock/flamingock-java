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

import io.flamingock.core.engine.audit.writer.AuditEntry;
import io.flamingock.core.engine.audit.writer.AuditStageStatus;
import io.flamingock.core.preview.PreviewStage;
import io.flamingock.core.task.executable.ExecutableTask;
import io.flamingock.core.task.executable.builder.ExecutableTaskBuilder;
import io.flamingock.core.task.loaded.AbstractLoadedTask;
import io.flamingock.core.task.loaded.LoadedTaskBuilder;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * It's the result of adding the loaded task to the ProcessDefinition
 */
public class LoadedStage {

    public static Builder builder() {
        return new Builder();
    }

    private final String name;

    private final Collection<AbstractLoadedTask> loadedTasks;

    private final boolean parallel;


    public LoadedStage(String name,
                       Collection<AbstractLoadedTask> loadedTasks,
                       boolean parallel) {
        this.name = name;
        this.loadedTasks = loadedTasks;
        this.parallel = parallel;

    }

    public ExecutableStage applyState(AuditStageStatus state) {

        Map<String, AuditEntry.Status> statesMap = state.getEntryStatesMap();

        List<ExecutableTask> tasks = loadedTasks
                .stream()
                .map(loadedTask -> ExecutableTaskBuilder.build(loadedTask, name, statesMap.get(loadedTask.getId())))
                .flatMap(List::stream)
                .collect(Collectors.toCollection(LinkedList::new));

        return new ExecutableStage(name, tasks, parallel);
    }

    public String getName() {
        return name;
    }


    public Collection<AbstractLoadedTask> getLoadedTasks() {
        return loadedTasks;
    }

    public boolean isParallel() {
        return parallel;
    }

    @Override
    public String toString() {
        return "LoadedStage{" + "name='" + name + '\'' +
                ", loadedTasks=" + loadedTasks +
                ", parallel=" + parallel +
                '}';
    }

    public static class Builder {

        private PreviewStage previewStage;

        private Builder() {
        }

        public Builder setPreviewStage(PreviewStage previewStage) {
            this.previewStage = previewStage;
            return this;
        }

        public LoadedStage build() {
            List<AbstractLoadedTask> loadedTasks = previewStage.getTasks()
                    .stream()
                    .map(LoadedTaskBuilder::build)
                    .collect(Collectors.toList());
            return new LoadedStage(previewStage.getName(), loadedTasks, previewStage.isParallel());
        }
    }
}
