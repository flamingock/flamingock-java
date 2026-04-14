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
package io.flamingock.cloud.api.response;

public abstract class LogAuditEntryResponse {

    private LockInfo lock;

    public LogAuditEntryResponse() {
    }

    public LogAuditEntryResponse(LockInfo lock) {
        this.lock = lock;
    }

    public LockInfo getLock() {
        return lock;
    }

    public void setLock(LockInfo lock) {
        this.lock = lock;
    }
}
