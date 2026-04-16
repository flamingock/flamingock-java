/*
 * Copyright 2025 Flamingock (https://www.flamingock.io)
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
package io.flamingock.common.test.cloud.mock;

import io.flamingock.cloud.api.vo.CloudChangeAction;
import io.flamingock.internal.common.core.targets.TargetSystemAuditMarkType;

public class MockRequestResponseChange {
    private final String changeId;
    private final TargetSystemAuditMarkType ongoingStatus;
    private final CloudChangeAction requiredAction;


    public MockRequestResponseChange(String changeId,
                                     TargetSystemAuditMarkType ongoingStatus) {
        this(changeId, ongoingStatus,  CloudChangeAction.APPLY);
    }

    public MockRequestResponseChange(String changeId,
                                     CloudChangeAction requiredAction) {
        this(changeId, TargetSystemAuditMarkType.NONE, requiredAction);
    }

    public MockRequestResponseChange(String changeId,
                                     TargetSystemAuditMarkType ongoingStatus,
                                     CloudChangeAction requiredAction) {
        this.changeId = changeId;
        this.ongoingStatus = ongoingStatus;
        this.requiredAction = requiredAction;
    }

    public String getChangeId() {
        return changeId;
    }

    public TargetSystemAuditMarkType getOngoingStatus() {
        return ongoingStatus;
    }

    public CloudChangeAction getRequiredAction() {
        return requiredAction;
    }

}
