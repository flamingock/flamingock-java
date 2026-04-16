/*
 * Copyright 2023 Flamingock (https://www.flamingock.io)
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
package io.flamingock.internal.core.external.targets.mark;

import io.flamingock.internal.common.core.targets.TargetSystemAuditMarkType;

public class TargetSystemAuditMark {


    private final String changeId;

    private final TargetSystemAuditMarkType operation;

    public TargetSystemAuditMark(String changeId, TargetSystemAuditMarkType operation) {
        this.changeId = changeId;
        this.operation = operation;
    }

    public String getChangeId() {
        return changeId;
    }

    public TargetSystemAuditMarkType getOperation() {
        return operation;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TargetSystemAuditMark that = (TargetSystemAuditMark) o;

        return changeId.equals(that.changeId);
    }

    @Override
    public int hashCode() {
        return changeId.hashCode();
    }
}
