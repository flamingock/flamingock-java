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
package io.flamingock.internal.core.pipeline.execution;

import io.flamingock.api.RecoveryStrategy;
import io.flamingock.api.annotations.NonLockGuarded;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.audit.AuditPersistence;
import io.flamingock.internal.common.core.audit.AuditPersistenceFactory;
import io.flamingock.internal.common.core.change.RecoveryDescriptor;
import io.flamingock.internal.common.core.context.Dependency;
import io.flamingock.internal.common.core.feature.Features;
import io.flamingock.internal.common.core.journal.JournalEvent;
import io.flamingock.internal.common.core.journal.JournalEventType;
import io.flamingock.internal.common.core.pipeline.StageDescriptor;
import io.flamingock.internal.common.core.recovery.action.ChangeAction;
import io.flamingock.internal.core.change.executable.CodeExecutableChange;
import io.flamingock.internal.core.change.loaded.AbstractReflectionLoadedChange;
import io.flamingock.internal.core.context.SimpleContext;
import io.flamingock.internal.core.external.store.AuditHistoryAppender;
import io.flamingock.internal.core.external.store.JournalHistoryAppender;
import io.flamingock.internal.core.external.store.lock.Lock;
import io.flamingock.internal.core.external.targets.TargetSystemManager;
import io.flamingock.internal.core.external.targets.operations.TargetSystemOps;
import io.flamingock.internal.core.journal.JournalEventReader;
import io.flamingock.internal.core.journal.JournalEventSequencer;
import io.flamingock.internal.core.journal.JournalEventSequencerFactory;
import io.flamingock.internal.core.runtime.ExecutionRuntime;
import io.flamingock.internal.core.runtime.MissingInjectedParameterException;
import io.flamingock.internal.util.FeatureFlag;
import io.flamingock.internal.util.Result;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Method;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

class StageExecutorHistoryAppenderTest {
    private static final AuditEntry HISTORY = new AuditEntry("old-execution", "historical-stage", "old-change",
            "original-author", LocalDateTime.of(2020, 1, 2, 3, 4), AuditEntry.Status.APPLIED,
            AuditEntry.ChangeType.STANDARD_CODE, "OriginalChange", "apply", "original.java", 42L,
            "original-host", Collections.singletonMap("original", "metadata"), false, null, null,
            "target", "001", RecoveryStrategy.MANUAL_INTERVENTION, false);
    private final Lock lock = mock(Lock.class);
    private final AtomicInteger guardedCalls = new AtomicInteger();
    private final JournalEventReader reader = mock(JournalEventReader.class);
    private final JournalEventSequencerFactory sequencerFactory = new JournalEventSequencerFactory(reader);

    @BeforeEach
    void resetFlagAndConfigureLock() {
        FeatureFlag.remove(Features.JOURNAL_EVENTS);
        doAnswer(invocation -> {
            guardedCalls.incrementAndGet();
            return null;
        }).when(lock).ensure();
        when(reader.getLastEventByStream("historical-stage")).thenReturn(Optional.empty());
    }

    @AfterEach
    void removeFlag() {
        FeatureFlag.remove(Features.JOURNAL_EVENTS);
    }

    @Test
    void auditConstructorAndApplyUseStagePersistenceWithJournalOff() throws Exception {
        AuditPersistence persistence = persistence(AuditHistoryAppender.class, JournalHistoryAppender.class);
        execute(persistence, AuditConsumer.class);
        verify((AuditHistoryAppender) persistence, times(2)).append(HISTORY);
        verify((JournalHistoryAppender<?>) persistence, never()).getSequencerFactory();
        verify((JournalHistoryAppender<?>) persistence, never()).append(any());
        verifyNoInteractions(reader);
        verifyNormalAudit(persistence);
        assertEquals(3, guardedCalls.get());
    }

    @Test
    void enabledJournalConstructorAndApplyHaveUsableFactoryAndResults() throws Exception {
        FeatureFlag.enable(Features.JOURNAL_EVENTS);
        AuditPersistence persistence = persistence(AuditHistoryAppender.class, JournalHistoryAppender.class);
        execute(persistence, JournalConsumer.class);
        verifyJournal(persistence);
        verifyNormalAudit(persistence);
        assertEquals(4, guardedCalls.get());
    }

    @Test
    void journalOnlyPersistenceIsIndependentOfAuditCapability() throws Exception {
        FeatureFlag.enable(Features.JOURNAL_EVENTS);
        AuditPersistence persistence = persistence(JournalHistoryAppender.class);
        execute(persistence, JournalConsumer.class);
        verifyJournal(persistence);
        verifyNormalAudit(persistence);
    }

    @Test
    void auditOnlyPersistenceIsIndependentOfJournalCapability() throws Exception {
        FeatureFlag.enable(Features.JOURNAL_EVENTS);
        AuditPersistence persistence = persistence(AuditHistoryAppender.class);
        execute(persistence, AuditConsumer.class);
        verify((AuditHistoryAppender) persistence, times(2)).append(HISTORY);
        verifyNormalAudit(persistence);
        verifyNoInteractions(reader);
    }

    @Test
    void disabledJournalConstructorRequestHasActionableMissingDependency() throws Exception {
        assertMissing(persistence(JournalHistoryAppender.class), JournalConsumer.class, JournalHistoryAppender.class);
        verifyNoInteractions(reader);
    }

    @Test
    void disabledJournalApplyRequestHasActionableMissingDependency() throws Exception {
        assertMissing(persistence(JournalHistoryAppender.class),
                JournalApplyConsumer.class, JournalHistoryAppender.class);
        verifyNoInteractions(reader);
    }

    @Test
    void unsupportedPersistenceRetainsNormalOperationWithEitherFlag() throws Exception {
        AuditPersistence persistence = persistence();
        execute(persistence, NormalConsumer.class);
        FeatureFlag.enable(Features.JOURNAL_EVENTS);
        execute(persistence, NormalConsumer.class);
        verify(persistence, times(4)).writeEntry(any());
        verifyNoInteractions(reader);
    }

    @Test
    void unsupportedCapabilitiesFailOnlyWhenRequested() throws Exception {
        FeatureFlag.enable(Features.JOURNAL_EVENTS);
        assertMissing(persistence(), AuditConsumer.class, AuditHistoryAppender.class);
        assertMissing(persistence(), JournalApplyConsumer.class, JournalHistoryAppender.class);
    }

    @Test
    void lostLockRejectsAppendBeforePersistenceCall() throws Exception {
        AuditPersistence persistence = persistence(AuditHistoryAppender.class);
        doAnswer(invocation -> {
            throw new IllegalStateException("execution lock lost");
        }).when(lock).ensure();
        StageExecutionException failure = assertThrows(StageExecutionException.class,
                () -> execute(persistence, AuditConsumer.class));
        assertTrue(causeMessages(failure).contains("execution lock lost"));
        verify((AuditHistoryAppender) persistence, never()).append(any());
    }

    @Test
    void registrationDoesNotAccessJournalWhenEnabledButNotRequested() throws Exception {
        FeatureFlag.enable(Features.JOURNAL_EVENTS);
        AuditPersistence persistence = persistence(AuditHistoryAppender.class, JournalHistoryAppender.class);
        execute(persistence, NormalConsumer.class);
        verify((JournalHistoryAppender<?>) persistence, never()).getSequencerFactory();
        verify((JournalHistoryAppender<?>) persistence, never()).append(any());
        verifyNoInteractions(reader);
        verifyNormalAudit(persistence);
    }

    private AuditPersistence persistence(Class<?>... capabilities) {
        AuditPersistence persistence = capabilities.length == 0 ? mock(AuditPersistence.class)
                : mock(AuditPersistence.class, withSettings().extraInterfaces(capabilities));
        when(persistence.writeEntry(any())).thenReturn(Result.OK());
        if (persistence instanceof AuditHistoryAppender) {
            when(((AuditHistoryAppender) persistence).append(any())).thenAnswer(invocation -> {
                assertTrue(guardedCalls.get() > 0, "append must execute under the lock guard");
                return Result.OK();
            });
        }
        if (persistence instanceof JournalHistoryAppender) {
            when(((JournalHistoryAppender<?>) persistence).getSequencerFactory()).thenAnswer(invocation -> {
                assertTrue(guardedCalls.get() > 0, "factory access must execute under the lock guard");
                return sequencerFactory;
            });
            when(((JournalHistoryAppender<?>) persistence).append(any())).thenReturn(Result.OK());
        }
        return persistence;
    }

    @SuppressWarnings("unchecked")
    private void execute(AuditPersistence persistence, Class<?> consumer) throws Exception {
        AbstractReflectionLoadedChange loaded = mock(AbstractReflectionLoadedChange.class);
        when(loaded.getId()).thenReturn("history-consumer");
        when(loaded.getOrder()).thenReturn(Optional.of("001"));
        doReturn(consumer.getConstructors()[0]).when(loaded).getConstructor();
        RecoveryDescriptor recovery = mock(RecoveryDescriptor.class);
        when(recovery.getStrategy()).thenReturn(RecoveryStrategy.MANUAL_INTERVENTION);
        when(loaded.getRecovery()).thenReturn(recovery);
        Method apply = java.util.Arrays.stream(consumer.getMethods())
                .filter(method -> method.getName().equals("apply")).findFirst().get();
        CodeExecutableChange<AbstractReflectionLoadedChange> change = new CodeExecutableChange<>(
                "executing-stage", loaded, ChangeAction.APPLY, apply, null);
        TargetSystemOps target = mock(TargetSystemOps.class);
        when(target.getId()).thenReturn("target");
        when(target.applyChange(any(), any())).thenAnswer(invocation -> {
            Function<ExecutionRuntime, Object> operation = invocation.getArgument(0);
            return operation.apply(invocation.getArgument(1));
        });
        TargetSystemManager manager = mock(TargetSystemManager.class);
        when(manager.getTargetSystem(loaded.getTargetSystem())).thenReturn(target);
        AuditPersistenceFactory<AuditPersistence> factory = mock(AuditPersistenceFactory.class);
        when(factory.get("executing-stage")).thenReturn(persistence);
        SimpleContext base = new SimpleContext();
        base.addDependency(new Dependency(JournalEventSequencerFactory.class, sequencerFactory));
        StageExecutor executor = new StageExecutor(base, Collections.emptySet(), factory, manager, null);
        assertEquals(1, executor.executeStage(new ExecutableStage("executing-stage", Collections.singletonList(change)),
                new ExecutionContext("execution", "host", Collections.emptyMap()), lock).getResult().getAppliedCount());
        verify(factory, times(1)).get("executing-stage");
    }

    private void assertMissing(AuditPersistence persistence, Class<?> consumer, Class<?> capability) throws Exception {
        StageExecutionException failure = assertThrows(StageExecutionException.class,
                () -> execute(persistence, consumer));
        Throwable cause = failure;
        while (!(cause instanceof MissingInjectedParameterException) && cause.getCause() != null) {
            cause = cause.getCause();
        }
        assertTrue(cause instanceof MissingInjectedParameterException, causeMessages(failure));
        assertEquals(capability, ((MissingInjectedParameterException) cause).getWrongParameter());
        assertTrue(cause.getMessage().contains("Dependency not found"));
        if (persistence instanceof JournalHistoryAppender) {
            verify((JournalHistoryAppender<?>) persistence, never()).getSequencerFactory();
            verify((JournalHistoryAppender<?>) persistence, never()).append(any());
        }
    }

    private void verifyNormalAudit(AuditPersistence persistence) {
        ArgumentCaptor<AuditEntry> entries = ArgumentCaptor.forClass(AuditEntry.class);
        verify(persistence, times(2)).writeEntry(entries.capture());
        assertEquals(AuditEntry.Status.STARTED, entries.getAllValues().get(0).getState());
        assertEquals(AuditEntry.Status.APPLIED, entries.getAllValues().get(1).getState());
        entries.getAllValues().forEach(entry -> assertEquals("executing-stage", entry.getStageId()));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void verifyJournal(AuditPersistence persistence) {
        ArgumentCaptor<JournalEvent<AuditEntry>> events = ArgumentCaptor.forClass((Class) JournalEvent.class);
        verify((JournalHistoryAppender<AuditEntry>) persistence, times(2)).append(events.capture());
        verify((JournalHistoryAppender<?>) persistence, times(2)).getSequencerFactory();
        verify(reader, times(2)).getLastEventByStream("historical-stage");
        for (JournalEvent<AuditEntry> event : events.getAllValues()) {
            assertSame(HISTORY, event.getData());
            assertEquals("historical-stage", event.getStreamId());
            assertEquals(1L, event.getStreamSequence());
            assertEquals("caller-event", event.getEventId());
            assertEquals("caller-key", event.getIdempotencyKey());
            assertEquals(3, event.getEventVersion());
            assertEquals(Instant.parse("2020-01-02T03:04:00Z"), event.getOccurredAt());
            assertTrue(event.isAcknowledged());
        }
    }

    private static String causeMessages(Throwable failure) {
        StringBuilder messages = new StringBuilder();
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            messages.append(cause.getMessage()).append('\n');
        }
        return messages.toString();
    }

    /** Exercises constructor and method resolution through the stage runtime. */
    public static class AuditConsumer {
        public AuditConsumer(AuditHistoryAppender appender, StageDescriptor stage) {
            assertEquals("executing-stage", stage.getName());
            assertTrue(appender.append(HISTORY) instanceof Result.Ok);
        }

        public void apply(AuditHistoryAppender appender) {
            assertTrue(appender.append(HISTORY) instanceof Result.Ok);
        }
    }

    /** Uses the supported payload type in both reflective injection signatures. */
    public static class JournalConsumer {
        public JournalConsumer(JournalHistoryAppender<AuditEntry> appender,
                               @NonLockGuarded JournalEventSequencerFactory expectedFactory) {
            appendHistory(appender, expectedFactory);
        }

        public void apply(JournalHistoryAppender<AuditEntry> appender,
                          @NonLockGuarded JournalEventSequencerFactory expectedFactory) {
            appendHistory(appender, expectedFactory);
        }
    }

    public static class JournalApplyConsumer {
        public JournalApplyConsumer() {
        }

        public void apply(JournalHistoryAppender<AuditEntry> appender) {
            assertTrue(appender.append(new JournalEvent<>("event", "key", JournalEventType.CHANGE_STATE,
                    "historical-stage", 1L, Instant.now(), HISTORY)) instanceof Result.Ok);
        }
    }

    public static class NormalConsumer {
        public NormalConsumer() {
        }

        public void apply(StageDescriptor stage) {
            assertEquals("executing-stage", stage.getName());
        }
    }

    private static void appendHistory(JournalHistoryAppender<AuditEntry> appender,
                                      JournalEventSequencerFactory expectedFactory) {
        JournalEventSequencerFactory actualFactory = appender.getSequencerFactory();
        assertSame(expectedFactory, actualFactory, "factory must be usable, not a recursively guarded return proxy");
        JournalEventSequencer sequencer = actualFactory.forStream("historical-stage");
        JournalEvent<AuditEntry> event = new JournalEvent<>("caller-event", "caller-key", JournalEventType.CHANGE_STATE,
                3, "historical-stage", sequencer.newEvent(HISTORY).getStreamSequence(),
                Instant.parse("2020-01-02T03:04:00Z"), HISTORY, true);
        assertTrue(appender.append(event) instanceof Result.Ok, "durable success must retain its Result subtype");
        sequencer.confirm();
        assertEquals(2L, sequencer.newEvent(HISTORY).getStreamSequence());
    }
}
