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

package io.flamingock.core.runner;

import io.flamingock.core.pipeline.ExecutablePipeline;
import io.flamingock.core.pipeline.ExecutableStage;
import io.flamingock.core.pipeline.execution.StageSummary;
import io.flamingock.core.task.executable.ExecutableTask;
import io.flamingock.core.pipeline.execution.TaskSummarizer;
import io.flamingock.core.task.navigation.step.complete.CompletedAlreadyAppliedStep;
import io.flamingock.core.task.navigation.summary.StepSummary;

import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;

public class PipelineSummary implements StepSummary {

    private final LinkedHashMap<String, StageSummary> stageSummaries = new LinkedHashMap<>();

    public PipelineSummary(ExecutablePipeline pipeline) {

        for (ExecutableStage executableStage : pipeline.getExecutableStages()) {
            StageSummary stageSummary = new StageSummary(executableStage.getName());

            for (ExecutableTask executableTask : executableStage.getTasks()) {
                if(executableTask.isAlreadyExecuted()) {
                    stageSummary.addSummary(new TaskSummarizer(executableTask.getId())
                            .add(new CompletedAlreadyAppliedStep(executableTask))
                            .getSummary());
                } else {
                    stageSummary.addSummary(new TaskSummarizer(executableTask.getId())
                            .addNotReachedTask(executableTask.getDescriptor())
                            .getSummary());
                }
            }
            stageSummaries.put(executableStage.getName(), stageSummary);
        }
    }

    public void merge(StageSummary stageSummary) {
        StageSummary presentStageSummary = stageSummaries.get(stageSummary.getId());
        if(presentStageSummary == null) {
            stageSummaries.put(stageSummary.getId(), stageSummary);
        } else {
            StageSummary mergedStageSummary = presentStageSummary.merge(stageSummary);
            stageSummaries.put(mergedStageSummary.getId(), mergedStageSummary);
        }

    }

    public void put(StageSummary stageSummary) {
        stageSummaries.put(stageSummary.getId(), stageSummary);
    }



    @Override
    public List<StageSummary> getLines() {
        return new LinkedList<>(stageSummaries.values());
    }
}
