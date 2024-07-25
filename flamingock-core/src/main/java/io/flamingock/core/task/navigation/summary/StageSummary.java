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

package io.flamingock.core.task.navigation.summary;

import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

public class StageSummary implements StepSummary {

    private final List<StepSummary> stageSummaries = new LinkedList<>();

    public void addSummary(StepSummary summary) {
        stageSummaries.add(summary);
    }

    public List<StepSummary> getSummaries() {
        return stageSummaries;
    }

    @Override
    public List<StepSummaryLine> getLines() {
        return getSummaries().stream()
                .map(StepSummary::getLines)
                .flatMap(List::stream)
                .collect(Collectors.toList());
    }
}
