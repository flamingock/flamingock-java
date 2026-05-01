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
package io.flamingock.cloud.lock.client;

import io.flamingock.cloud.api.response.LockInfoResponse;
import io.flamingock.internal.core.external.store.lock.LockKey;
import io.flamingock.internal.util.id.RunnerId;

/**
 * Wire-level cloud lock client. Maps directly to the runner application's lock REST API
 * documented in {@code cloud/flamingock-cloud/docs/lock-api-endpoints.md}.
 *
 * <p>The doc defines four server endpoints — acquire, extend, release, get. Acquisition is
 * never invoked through this interface (the cloud lock is materialised from the
 * execution-plan response), so only extend, release and the read-only get are exposed.</p>
 */
public interface LockServiceClient {

    /**
     * Issues {@code POST /api/v1/{key}/lock/extension} as the current owner. The server
     * returns a {@code LockResponse { status: "EXTENDED", lock: {...} }} wrapper; this
     * method unwraps and returns the inner lock state.
     */
    LockInfoResponse extendLock(LockKey lockKey, RunnerId runnerId);

    /**
     * Issues {@code GET /api/v1/{key}/lock}. Read-only lookup of the current lock state for
     * this key; ownership is not enforced by the server (any authenticated runner can read).
     * Returns {@code null} when the server responds {@code 404 R_LOCK_03} ("no lock for this
     * key in this environment"). Other transport / server errors propagate as
     * {@link io.flamingock.internal.util.ServerException}.
     */
    LockInfoResponse getLockInfo(LockKey lockKey, RunnerId runnerId);

    /**
     * Issues {@code DELETE /api/v1/{key}/lock} as the current owner. Server response is
     * intentionally discarded.
     */
    void releaseLock(LockKey lockKey, RunnerId runnerId);
}
