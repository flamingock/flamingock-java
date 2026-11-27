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
package io.flamingock.internal.core.external.store;

import io.flamingock.internal.common.core.audit.AuditPersistence;
import io.flamingock.internal.common.core.audit.AuditPersistenceFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Keeps stage persistence instances for the lifetime of one runner. */
public final class RunnerAuditPersistenceFactory implements AuditPersistenceFactory<AuditPersistence> {
    private final AuditPersistenceFactory<? extends AuditPersistence> delegate;
    private final Map<String, AuditPersistence> instances = new HashMap<>();

    public RunnerAuditPersistenceFactory(AuditPersistenceFactory<? extends AuditPersistence> delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public synchronized AuditPersistence get(String stageId) {
        if (!instances.containsKey(stageId)) {
            instances.put(stageId, delegate.get(stageId));
        }
        return instances.get(stageId);
    }
}
