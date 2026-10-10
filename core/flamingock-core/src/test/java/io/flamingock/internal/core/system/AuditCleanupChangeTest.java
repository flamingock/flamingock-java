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
package io.flamingock.internal.core.system;

import io.flamingock.internal.common.core.error.FlamingockException;
import io.flamingock.internal.common.core.feature.Features;
import io.flamingock.internal.core.external.store.AuditCompactor;
import io.flamingock.internal.util.FeatureFlag;
import io.flamingock.internal.util.Result;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the three decisions {@link AuditCleanupChange} makes: refuse to run without a journal, treat a
 * reported compaction failure as a change failure, and otherwise compact exactly once.
 * <p>
 * No framework and no container — the class takes its capability as a parameter, so a counting lambda is
 * the whole test double. The compaction behaviour itself is covered per store by each
 * {@code AuditCompactor} implementation's own tests and the shared conformance suite.
 */
class AuditCleanupChangeTest {

    private final AuditCleanupChange change = new AuditCleanupChange();

    @AfterEach
    void tearDown() {
        // FeatureFlag is process-global; leaving it set would change how later tests behave.
        FeatureFlag.remove(Features.JOURNAL_EVENTS);
    }

    @Test
    @DisplayName("compacts exactly once when journal events are enabled")
    void compactsWhenJournalEventsAreEnabled() {
        FeatureFlag.enable(Features.JOURNAL_EVENTS);
        AtomicInteger invocations = new AtomicInteger();

        change.compactAuditStore(() -> {
            invocations.incrementAndGet();
            return Result.OK();
        });

        assertEquals(1, invocations.get());
    }

    @Test
    @DisplayName("refuses to compact when journal events are disabled, without touching the store")
    void failsWhenJournalEventsAreDisabled() {
        AtomicInteger invocations = new AtomicInteger();

        FlamingockException exception = assertThrows(FlamingockException.class,
                () -> change.compactAuditStore(() -> {
                    invocations.incrementAndGet();
                    return Result.OK();
                }));

        // The important half: it must fail *before* compacting. With the journal off, the audit store holds
        // the only copy of the history, so compacting it would be destructive rather than merely premature.
        assertEquals(0, invocations.get(), "compaction must not be attempted without a journal");
        assertTrue(exception.getMessage().contains("journal"), exception.getMessage());
    }

    @Test
    @DisplayName("the refusal explains what to do about it")
    void theFailureMessageExplainsWhy() {
        FlamingockException exception = assertThrows(FlamingockException.class,
                () -> change.compactAuditStore(Result::OK));

        // Worth asserting rather than trusting: an operator hitting a failed system change at startup will
        // reach for the quickest way to make it stop. If the message does not say that the journal is what
        // protects the history, removing the module looks like a fix and silently loses the protection.
        String message = exception.getMessage();
        assertTrue(message.contains("journal events"), message);
        assertTrue(message.contains("history"), message);
        assertTrue(message.contains("backfill"), message);
    }

    @Test
    @DisplayName("a reported compaction failure becomes a change failure, preserving the cause")
    void failsWhenCompactionReportsAnError() {
        FeatureFlag.enable(Features.JOURNAL_EVENTS);
        IllegalStateException reported = new IllegalStateException("store said no");

        FlamingockException exception = assertThrows(FlamingockException.class,
                () -> change.compactAuditStore(() -> new Result.Error(reported)));

        // The capability reports, the change decides. Swallowing this would record the change as applied
        // over a store that was never compacted.
        assertSame(reported, exception.getCause(), "the reported cause must survive");
    }

    @Test
    @DisplayName("compactAuditStore is the only method, so apply-method resolution stays name-based")
    void compactAuditStoreIsTheOnlyMethod() {
        int declared = 0;
        for (Method method : AuditCleanupChange.class.getDeclaredMethods()) {
            if (!method.isSynthetic()) {
                declared++;
            }
        }

        // Apply-method resolution short-circuits when exactly one declared method matches by name, which is
        // why the change's declared PreviewMethod parameter types never have to track this signature.
        // Adding a second method - or an overload - moves resolution onto exact type matching and makes
        // those declared types suddenly load-bearing.
        assertEquals(1, declared,
                "adding a method here changes how the framework resolves the apply method");
    }
}
