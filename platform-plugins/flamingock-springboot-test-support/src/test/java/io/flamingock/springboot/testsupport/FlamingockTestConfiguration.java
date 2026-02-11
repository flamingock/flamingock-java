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
package io.flamingock.springboot.testsupport;

import io.flamingock.api.external.TargetSystem;
import io.flamingock.core.kit.inmemory.InternalInMemoryAuditStorage;
import io.flamingock.core.kit.inmemory.InternalInMemoryLockStorage;
import io.flamingock.core.kit.inmemory.InternalInMemoryTestAuditStore;
import io.flamingock.internal.core.external.store.CommunityAuditStore;
import io.flamingock.targetsystem.nontransactional.NonTransactionalTargetSystem;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Test configuration providing Flamingock infrastructure beans for Spring Boot tests.
 */
@Configuration
public class FlamingockTestConfiguration {

    @Bean
    public CommunityAuditStore auditStore() {
        InternalInMemoryAuditStorage auditStorage = new InternalInMemoryAuditStorage();
        InternalInMemoryLockStorage lockStorage = new InternalInMemoryLockStorage();
        return new InternalInMemoryTestAuditStore(auditStorage, lockStorage);
    }

    @Bean
    public TargetSystem testTargetSystem() {
        return new NonTransactionalTargetSystem("test-system");
    }
}
