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
import io.flamingock.internal.common.core.feature.Features;
import io.flamingock.internal.util.FeatureFlag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

class AuditStoreTest {

    @AfterEach
    void tearDown() {
        FeatureFlag.remove(Features.JOURNAL_EVENTS);
    }

    @Test
    void defaultPersistenceFactoryUsesLegacyPersistenceWhenJournalEventsAreDisabled() {
        AuditStore<AuditPersistence> auditStore = mock(AuditStore.class, CALLS_REAL_METHODS);
        AuditPersistence persistence = mock(AuditPersistence.class);
        doReturn(persistence).when(auditStore).getPersistence();

        assertSame(persistence, auditStore.getPersistenceFactory().get("stage"));
    }

    @Test
    void defaultPersistenceFactoryFailsFastWhenJournalEventsAreEnabled() {
        FeatureFlag.enable(Features.JOURNAL_EVENTS);
        AuditStore<AuditPersistence> auditStore = mock(AuditStore.class, CALLS_REAL_METHODS);

        assertThrows(IllegalStateException.class, auditStore::getPersistenceFactory);
    }

    @Test
    void defaultPersistenceRequiresStageAwareFactory() {
        AuditStore<AuditPersistence> auditStore = mock(AuditStore.class, CALLS_REAL_METHODS);

        assertThrows(IllegalStateException.class, auditStore::getPersistence);
    }
}
