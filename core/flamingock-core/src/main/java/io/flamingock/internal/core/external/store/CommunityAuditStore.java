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
package io.flamingock.internal.core.external.store;

import io.flamingock.internal.core.external.store.audit.community.CommunityAuditPersistence;
import io.flamingock.internal.core.external.store.lock.community.CommunityLockService;

public interface CommunityAuditStore extends AuditStore<CommunityAuditPersistence> {

    CommunityLockService getLockService();

    /**
     * Returns the compaction face for this store.
     * <p>
     * Abstract rather than a defaulted no-op on purpose: core calls it unconditionally, so a store that
     * cannot compact is a compile error rather than something to be discovered at runtime. It lives here
     * instead of on {@link AuditStore} because only Community keeps a local audit store — the Cloud edition
     * has no local audit table to compact.
     * <p>
     * A face the store provides, rather than something the store itself implements, so that compaction
     * cannot be reached from the store's operational surface and so that it can be injected into a change
     * as a one-method object.
     * <p>
     * May only be called after {@link #initialize}, since stores build their repositories there.
     *
     * @return the compactor bound to this store's audit repository
     * @throws IllegalStateException if the store has not been initialized
     */
    AuditCompactor getAuditCompactor();
}
