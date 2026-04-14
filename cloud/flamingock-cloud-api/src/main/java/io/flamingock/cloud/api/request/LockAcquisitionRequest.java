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
package io.flamingock.cloud.api.request;

public class LockAcquisitionRequest {

    private long acquiredForMillis;
    private String lastAcquisitionId;
    private Long elapsedMillis;

    public LockAcquisitionRequest() {
    }

    public LockAcquisitionRequest(long acquiredForMillis, String lastAcquisitionId, Long elapsedMillis) {
        this.acquiredForMillis = acquiredForMillis;
        this.lastAcquisitionId = lastAcquisitionId;
        this.elapsedMillis = elapsedMillis;
    }

    public long getAcquiredForMillis() {
        return acquiredForMillis;
    }

    public void setAcquiredForMillis(long acquiredForMillis) {
        this.acquiredForMillis = acquiredForMillis;
    }

    public String getLastAcquisitionId() {
        return lastAcquisitionId;
    }

    public void setLastAcquisitionId(String lastAcquisitionId) {
        this.lastAcquisitionId = lastAcquisitionId;
    }

    public Long getElapsedMillis() {
        return elapsedMillis;
    }

    public void setElapsedMillis(Long elapsedMillis) {
        this.elapsedMillis = elapsedMillis;
    }
}
