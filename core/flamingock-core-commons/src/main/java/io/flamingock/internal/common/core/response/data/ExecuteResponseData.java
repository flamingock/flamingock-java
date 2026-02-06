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
package io.flamingock.internal.common.core.response.data;

import com.fasterxml.jackson.annotation.JsonTypeName;

/**
 * Response data for the EXECUTE operation.
 * Currently a placeholder - detailed results will be available in a future version.
 */
@JsonTypeName("execute")
public class ExecuteResponseData {

    private String message;

    public ExecuteResponseData() {
    }

    public ExecuteResponseData(String message) {
        this.message = message;
    }

    public static ExecuteResponseData placeholder() {
        return new ExecuteResponseData("Execution completed. Detailed results will be available in a future version.");
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
