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
package io.flamingock.cloud.api.vo;

/**
 * Wire-level per-stage status sent from the client to the cloud planner.
 *
 * <p>Mirrors the internal {@code StageState} hierarchy in shape; the cloud server uses this to
 * decide what to do with each stage on the next iteration (e.g., skip stages already failed
 * or blocked, route MI cases). Client-side mapping lives in {@code CloudApiMapper.toCloud(StageState)}.
 */
public enum CloudStageStatus {
    NOT_STARTED,
    STARTED,
    COMPLETED,
    FAILED,
    BLOCKED_MANUAL_INTERVENTION
}
