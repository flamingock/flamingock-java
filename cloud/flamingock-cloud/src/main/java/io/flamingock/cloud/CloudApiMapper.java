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
package io.flamingock.cloud;

import io.flamingock.cloud.api.vo.CloudAuditStatus;
import io.flamingock.cloud.api.vo.CloudChangeType;
import io.flamingock.cloud.api.vo.CloudStageStatus;
import io.flamingock.cloud.api.vo.CloudTargetSystemAuditMarkType;
import io.flamingock.cloud.api.vo.CloudTxStrategy;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.audit.AuditTxType;
import io.flamingock.internal.common.core.response.data.StageState;
import io.flamingock.internal.common.core.targets.TargetSystemAuditMarkType;

public final class CloudApiMapper {

    private CloudApiMapper() {
    }

    public static CloudTargetSystemAuditMarkType toCloud(TargetSystemAuditMarkType domain) {
        return CloudTargetSystemAuditMarkType.valueOf(domain.name());
    }

    public static CloudTxStrategy toCloud(AuditTxType txType) {
        return CloudTxStrategy.valueOf(txType.name());
    }

    public static CloudAuditStatus toCloud(AuditEntry.Status status) {
        return CloudAuditStatus.valueOf(status.name());
    }

    public static CloudChangeType toCloud(AuditEntry.ChangeType changeType) {
        return CloudChangeType.valueOf(changeType.name());
    }

    /**
     * Maps the internal {@link StageState} hierarchy to the wire enum {@link CloudStageStatus}.
     *
     * <p>Returns {@code null} for {@code NOT_STARTED} (or a null state) — the canonical wire
     * shape for "not started" is field absence/null, matching back-compat semantics with older
     * clients that don't populate the field. The server treats {@code null} as {@code NOT_STARTED}.
     *
     * <p>Order is important: {@code BlockedForMI} extends {@code Failed}, so the
     * blocked-for-MI check must come before the generic failed check.
     */
    public static CloudStageStatus toCloud(StageState state) {
        if (state == null || state.isNotStarted()) return null;
        if (state.isBlockedForManualIntervention()) return CloudStageStatus.BLOCKED_MANUAL_INTERVENTION;
        if (state.isFailed()) return CloudStageStatus.FAILED;
        if (state.isCompleted()) return CloudStageStatus.COMPLETED;
        if (state.isStarted()) return CloudStageStatus.STARTED;
        throw new IllegalStateException("Unknown StageState: " + state);
    }

}
