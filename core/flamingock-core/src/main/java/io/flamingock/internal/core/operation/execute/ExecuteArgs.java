/*
 * Copyright 2026 Flamingock (https://www.flamingock.io)
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
package io.flamingock.internal.core.operation.execute;

import io.flamingock.internal.core.operation.OperationArgs;
import io.flamingock.internal.core.pipeline.loaded.LoadedPipeline;

public class ExecuteArgs implements OperationArgs {

    private final LoadedPipeline pipeline;

    public ExecuteArgs(LoadedPipeline pipeline) {
        this.pipeline = pipeline;
    }

    public LoadedPipeline getPipeline() {
        return pipeline;
    }
}
