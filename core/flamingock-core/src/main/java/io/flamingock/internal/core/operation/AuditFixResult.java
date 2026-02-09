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
package io.flamingock.internal.core.operation;

import io.flamingock.internal.common.core.recovery.FixResult;
import io.flamingock.internal.common.core.recovery.Resolution;
import io.flamingock.internal.common.core.response.data.AuditFixResponseData;

public class AuditFixResult extends AbstractOperationResult {

    private final String changeId;
    private final Resolution resolution;
    private final FixResult fixResult;

    public AuditFixResult(String changeId, Resolution resolution, FixResult fixResult) {
        this.changeId = changeId;
        this.resolution = resolution;
        this.fixResult = fixResult;
    }

    public String getChangeId() {
        return changeId;
    }

    public Resolution getResolution() {
        return resolution;
    }

    public FixResult getFixResult() {
        return fixResult;
    }

    @Override
    public Object toResponseData() {
        String message = fixResult == FixResult.APPLIED
                ? String.format("Change '%s' successfully marked as %s", changeId, resolution.name())
                : String.format("No issue found for change '%s'", changeId);
        return new AuditFixResponseData(
                changeId,
                resolution.name(),
                fixResult.name(),
                message
        );
    }
}
