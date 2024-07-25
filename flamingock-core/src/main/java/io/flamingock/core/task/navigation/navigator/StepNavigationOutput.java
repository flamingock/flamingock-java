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

package io.flamingock.core.task.navigation.navigator;

import io.flamingock.core.task.navigation.summary.StepSummary;
import io.flamingock.core.task.navigation.summary.StepSummaryLine;

import java.util.List;

public class StepNavigationOutput implements StepSummary {

    private final boolean success;

    private final StepSummary summary;

    public StepNavigationOutput(boolean success, StepSummary summary) {
        this.summary = summary;
        this.success = success;
    }

    @Override
    public List<StepSummaryLine> getLines() {
        return summary.getLines();
    }

    @Override
    public String getPretty() {
        return summary.getPretty();
    }


    public boolean isFailed() {
        return !success;
    }
}
