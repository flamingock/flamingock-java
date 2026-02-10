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

import io.flamingock.internal.common.core.response.data.ExecuteResponseData;
import io.flamingock.internal.core.operation.AbstractOperationResult;

/**
 * Result of executing the pipeline.
 * Contains structured result data for reporting and CLI output.
 */
public class ExecuteResult extends AbstractOperationResult {

    private final ExecuteResponseData data;

    public ExecuteResult(ExecuteResponseData data) {
        this.data = data;
    }

    public ExecuteResponseData getData() {
        return data;
    }

    @Override
    public Object toResponseData() {
        return data;
    }
}
