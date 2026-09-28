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
package io.flamingock.internal.core.builder;

import io.flamingock.internal.common.core.audit.AuditHistoryAppender;
import io.flamingock.internal.common.core.audit.JournalHistoryAppender;
import io.flamingock.internal.core.context.PriorityContext;
import io.flamingock.internal.core.context.SimpleContext;
import io.flamingock.internal.core.external.store.HistoryAppenderProvider;
import io.flamingock.internal.core.external.store.AuditStore;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HistoryAppenderProviderTest {

    @Test
    void registersProvidedAppendersByTypeForContextResolution() {
        HistoryAppenderProvider provider = mock(HistoryAppenderProvider.class);
        AuditHistoryAppender appender = mock(AuditHistoryAppender.class);
        JournalHistoryAppender journal = mock(JournalHistoryAppender.class);
        when(provider.getAuditHistoryAppender()).thenReturn(appender);
        when(provider.getJournalHistoryAppender()).thenReturn(journal);
        PriorityContext context = context();

        AbstractChangeRunnerBuilder.registerHistoryAppenders(context, provider);

        assertSame(appender, context.getDependency(AuditHistoryAppender.class).get().getInstance());
        assertSame(journal, context.getDependency(JournalHistoryAppender.class).get().getInstance());
    }

    @Test
    void skipsUnavailableAppendersAndStoresWithoutTheCapability() {
        PriorityContext context = context();
        HistoryAppenderProvider provider = mock(HistoryAppenderProvider.class);
        AbstractChangeRunnerBuilder.registerHistoryAppenders(context, provider);
        AbstractChangeRunnerBuilder.registerHistoryAppenders(context, mock(AuditStore.class));

        assertFalse(context.getDependency(AuditHistoryAppender.class).isPresent());
        assertFalse(context.getDependency(JournalHistoryAppender.class).isPresent());
    }

    private PriorityContext context() {
        return new PriorityContext(new SimpleContext());
    }
}
