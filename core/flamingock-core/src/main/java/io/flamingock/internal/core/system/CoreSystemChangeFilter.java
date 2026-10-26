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

import io.flamingock.internal.common.core.feature.Features;
import io.flamingock.internal.core.change.filter.ChangeFilter;
import io.flamingock.internal.core.change.loaded.AbstractLoadedChange;
import io.flamingock.internal.util.FeatureFlag;

/**
 * Excludes the journal-backfill and audit-cleanup system changes from the runtime pipeline while
 * {@link Features#JOURNAL_EVENTS} is disabled.
 * <p>
 * They are contributed unconditionally at compile time - {@code CoreSystemChangeProvider} has no way to
 * see the runtime flag, since it runs inside javac, a different process. This filter is the runtime-side
 * half: it runs in the same process where {@link FeatureFlag} state actually lives, so it can keep them
 * out of the executable pipeline while the flag is off.
 * <p>
 * Excluding rather than letting them run and throw (their own fail-fast behavior, kept for when they are
 * genuinely in the pipeline but something is otherwise wrong) matters because of what "ran and threw" does
 * to the audit store: nothing, since the failure is thrown before any audit write - but letting every
 * application startup reach that throw would turn an internal, off-by-default flag into a hard failure for
 * every application, which defeats the point of it being off by default. Excluded here, the change simply
 * never attempts to run and never gets an audit record, so the first startup after the flag is finally
 * enabled sees it for the first time and runs it then - never "skipped but recorded forever".
 */
public final class CoreSystemChangeFilter implements ChangeFilter {

    private static final CoreSystemChangeFilter INSTANCE = new CoreSystemChangeFilter();

    public static CoreSystemChangeFilter instance() {
        return INSTANCE;
    }

    private CoreSystemChangeFilter() {
    }

    @Override
    public boolean filter(AbstractLoadedChange change) {
        String id = change.getId();
        boolean gated = JournalBackfillChange.CHANGE_ID.equals(id) || AuditCleanupChange.CHANGE_ID.equals(id);
        return !gated || FeatureFlag.isEnabled(Features.JOURNAL_EVENTS);
    }
}
