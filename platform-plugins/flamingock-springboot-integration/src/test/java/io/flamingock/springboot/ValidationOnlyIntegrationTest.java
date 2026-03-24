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
package io.flamingock.springboot;

import io.flamingock.api.external.TargetSystem;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.error.PendingChangesException;
import io.flamingock.internal.core.external.store.CommunityAuditStore;
import io.flamingock.internal.core.external.store.audit.community.CommunityAuditPersistence;
import io.flamingock.internal.core.external.store.lock.LockAcquisition;
import io.flamingock.internal.core.external.store.lock.community.CommunityLockService;
import io.flamingock.internal.util.id.RunnerId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Spring Boot end-to-end integration tests for the {@code validationOnly} mode.
 *
 * <p>Uses {@code INITIALIZING_BEAN} management mode so that Flamingock runs synchronously
 * during application context startup — enabling the context to fail fast when pending changes
 * are detected.
 */
class ValidationOnlyIntegrationTest {

    private static final String CHANGE_ID = "validation-only-test-change";

    /**
     * Builds an ApplicationContextRunner pre-configured with INITIALIZING_BEAN mode
     * and validation-only enabled, wired to a specific audit configuration.
     */
    private ApplicationContextRunner contextRunner(Class<?> configClass) {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(FlamingockAutoConfiguration.class))
                .withUserConfiguration(configClass)
                .withPropertyValues(
                        "spring.profiles.active=non-cli",
                        "flamingock.management-mode=INITIALIZING_BEAN",
                        "flamingock.validation-only=true"
                );
    }

    @Test
    @DisplayName("validationOnly=true + pending changes → context fails with PendingChangesException")
    void whenValidationOnlyAndPendingChanges_thenContextFailsWithPendingChangesException() {
        contextRunner(PendingChangesConfiguration.class).run(ctx -> {
            assertThat(ctx).hasFailed();

            Throwable failure = ctx.getStartupFailure();
            Throwable rootCause = getRootCause(failure);

            assertThat(rootCause)
                    .isInstanceOf(PendingChangesException.class);
        });
    }

    @Test
    @DisplayName("validationOnly=true + all changes applied → context starts successfully")
    void whenValidationOnlyAndAllChangesApplied_thenContextStartsSuccessfully() {
        contextRunner(AllChangesAppliedConfiguration.class).run(ctx -> {
            assertThat(ctx).hasNotFailed();
        });
    }

    // ─────────────────────────── Helpers ───────────────────────────

    private static Throwable getRootCause(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }

    /**
     * Creates a fully-stubbed {@link CommunityAuditStore} mock.
     * Both {@link CommunityAuditPersistence} and {@link CommunityLockService} are mocked
     * and wired into the store.
     *
     * @param auditHistory the audit history to return from {@code getAuditHistory()}
     */
    private static CommunityAuditStore buildAuditStoreMock(List<AuditEntry> auditHistory) {
        CommunityAuditPersistence persistence = mock(CommunityAuditPersistence.class);
        CommunityLockService lockService = mock(CommunityLockService.class);

        // Stub audit history — determines whether changes are "pending"
        when(persistence.getAuditHistory()).thenReturn(auditHistory);

        // Delegate snapshot to the default interface method (builds from getAuditHistory())
        when(persistence.getAuditSnapshotByChangeId()).thenCallRealMethod();

        // Stub closer — called by both the operation and the runner finalizer
        when(persistence.getCloser()).thenReturn(() -> { });

        // Stub lock acquisition — required when there are pending changes
        when(lockService.upsert(any(), any(RunnerId.class), anyLong()))
                .thenAnswer(invocation -> new LockAcquisition(
                        invocation.getArgument(1),
                        invocation.getArgument(2)
                ));

        // Stub lock extension — called by the LockRefreshDaemon thread
        when(lockService.extendLock(any(), any(RunnerId.class), anyLong()))
                .thenAnswer(invocation -> new LockAcquisition(
                        invocation.getArgument(1),
                        invocation.getArgument(2)
                ));

        CommunityAuditStore auditStore = mock(CommunityAuditStore.class);
        when(auditStore.getPersistence()).thenReturn(persistence);
        when(auditStore.getLockService()).thenReturn(lockService);

        return auditStore;
    }

    // ─────────────────────────── Spring Configurations ───────────────────────────

    /**
     * Configuration for the "pending changes" scenario.
     * The audit history is empty → all pipeline changes are considered pending.
     */
    @Configuration
    static class PendingChangesConfiguration {

        @Bean
        public List<TargetSystem> targetSystems() {
            return new ArrayList<>();
        }

        @Bean
        public CommunityAuditStore auditStore() {
            return buildAuditStoreMock(Collections.emptyList());
        }
    }

    /**
     * Configuration for the "all changes applied" scenario.
     * The audit history contains an APPLIED entry for the test change → no execution needed.
     */
    @Configuration
    static class AllChangesAppliedConfiguration {

        @Bean
        public List<TargetSystem> targetSystems() {
            return new ArrayList<>();
        }

        @Bean
        public CommunityAuditStore auditStore() {
            AuditEntry appliedEntry = new AuditEntry(
                    "exec-id-001",
                    "test-stage",
                    CHANGE_ID,
                    "test",
                    LocalDateTime.now().minusMinutes(5),
                    AuditEntry.Status.APPLIED,
                    AuditEntry.ChangeType.STANDARD_CODE,
                    "io.flamingock.springboot.test._001__ValidationOnlyTestChange",
                    "apply",
                    null,
                    100L,
                    "localhost",
                    null,
                    false,
                    null
            );
            return buildAuditStoreMock(Collections.singletonList(appliedEntry));
        }
    }
}
