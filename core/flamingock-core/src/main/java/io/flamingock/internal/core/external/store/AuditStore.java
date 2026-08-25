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

import io.flamingock.api.external.ExternalSystem;
import io.flamingock.internal.common.core.audit.AuditPersistenceFactory;
import io.flamingock.internal.common.core.audit.AuditReader;
import io.flamingock.internal.common.core.audit.AuditWriter;
import io.flamingock.internal.common.core.context.ContextInitializable;
import io.flamingock.internal.common.core.audit.AuditPersistence;
import io.flamingock.internal.common.core.feature.Features;
import io.flamingock.internal.util.FeatureFlag;

import java.util.Collections;
import java.util.Set;

public interface AuditStore<PERSISTENCE extends AuditPersistence> extends ExternalSystem, ContextInitializable {

    @Deprecated
    default PERSISTENCE getPersistence() {
        throw new IllegalStateException("Audit stores must provide persistence through getPersistenceFactory(stageId)");
    }

    default AuditReader getAuditReader() {
        return getPersistence();
    }

    //TODO temporally default, until we implement the other DB stores
    default AuditPersistenceFactory<PERSISTENCE> getPersistenceFactory() {
        if (FeatureFlag.isEnabled(Features.JOURNAL_EVENTS, false)) {
            throw new IllegalStateException("Journal-enabled audit stores require getPersistenceFactory(stageId)");
        }
        return stageId -> getPersistence();
    }

    default Runnable getCloser() {
        return () -> {
        };
    }

    //TODO move this to TargetSystem
    default Set<Class<?>> getNonGuardedTypes() {
        return Collections.emptySet();
    }

}
