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
package io.flamingock.core.processor.system;

import io.flamingock.internal.common.core.audit.AuditPersistenceFactory;
import io.flamingock.internal.common.core.change.RecoveryDescriptor;
import io.flamingock.internal.common.core.change.TargetSystemDescriptor;
import io.flamingock.internal.common.core.pipeline.PipelineHelper;
import io.flamingock.internal.common.core.preview.CodePreviewChange;
import io.flamingock.internal.common.core.preview.PreviewConstructor;
import io.flamingock.internal.common.core.preview.PreviewMethod;
import io.flamingock.internal.common.core.preview.builder.CodePreviewChangeBuilder;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Contributes the core system changes that must run on every application startup, unconditionally - unlike
 * the Mongock importer, they are not discovered from a user annotation, because they do not depend on any
 * module the user chose to add: every audit store needs its history preserved to the journal, then
 * compacted to one record per change.
 * <p>
 * Deliberately references {@code io.flamingock.internal.core.system.JournalBackfillChange},
 * {@code AuditCleanupChange} and {@code AuditCompactor} by fully-qualified string name rather than by
 * class literal. {@code flamingock-processor} must stay free of a compile dependency on
 * {@code flamingock-core}: this module's own compile classpath doubles as every consuming project's
 * annotation-processor classpath, so pulling flamingock-core in here would leak it onto that classpath for
 * every project that merely applies the processor - changing what else the processor can see and resolve
 * while it runs (see {@code MultiModuleProcessingIT#builderClass} for a validator that depends on
 * flamingock-core being absent from exactly this classpath). The identifiers below must be kept in sync by
 * hand with their source classes.
 * <p>
 * Reserved order sequence: Mongock import {@code "00100"} (conditional, contributed by
 * {@code MongockAnnotationProcessorPlugin} when {@code mongock-support} is present), journal backfill
 * {@code "00200"}, audit cleanup {@code "00300"}. Both changes here resolve against the reserved
 * {@link PipelineHelper#AUDIT_STORE_TARGET_SYSTEM} alias, since they operate on the audit store itself
 * rather than on a target system the user declared.
 */
public final class CoreSystemChangeProvider {

    // Must match io.flamingock.internal.core.system.JournalBackfillChange.CHANGE_ID / .class.getName()
    private static final String JOURNAL_BACKFILL_CHANGE_ID = "backfill-audit-history-to-journal";
    private static final String JOURNAL_BACKFILL_CLASS = "io.flamingock.internal.core.system.JournalBackfillChange";
    private static final String JOURNAL_BACKFILL_APPLY_METHOD = "journalBackfill";

    // Must match io.flamingock.internal.core.system.AuditCleanupChange.CHANGE_ID / .class.getName()
    private static final String AUDIT_CLEANUP_CHANGE_ID = "compact-audit-store";
    private static final String AUDIT_CLEANUP_CLASS = "io.flamingock.internal.core.system.AuditCleanupChange";
    private static final String AUDIT_CLEANUP_APPLY_METHOD = "compactAuditStore";

    // Must match io.flamingock.internal.core.external.store.AuditCompactor.class.getName()
    private static final String AUDIT_COMPACTOR_CLASS = "io.flamingock.internal.core.external.store.AuditCompactor";

    private static final TargetSystemDescriptor AUDIT_STORE_TARGET_SYSTEM =
            new TargetSystemDescriptor(PipelineHelper.AUDIT_STORE_TARGET_SYSTEM);

    private CoreSystemChangeProvider() {
    }

    public static List<CodePreviewChange> getSystemChanges() {
        return Arrays.asList(
                buildSystemChange(JOURNAL_BACKFILL_CHANGE_ID, "00200", JOURNAL_BACKFILL_CLASS,
                        JOURNAL_BACKFILL_APPLY_METHOD, AuditPersistenceFactory.class.getName()),
                buildSystemChange(AUDIT_CLEANUP_CHANGE_ID, "00300", AUDIT_CLEANUP_CLASS,
                        AUDIT_CLEANUP_APPLY_METHOD, AUDIT_COMPACTOR_CLASS));
    }

    private static CodePreviewChange buildSystemChange(String id,
                                                        String order,
                                                        String sourceClass,
                                                        String applyMethod,
                                                        String applyMethodParam) {
        CodePreviewChangeBuilder builder = CodePreviewChangeBuilder.instance();
        builder.setId(id);
        builder.setOrder(order);
        builder.setAuthor("flamingock-team");
        builder.setTargetSystem(AUDIT_STORE_TARGET_SYSTEM);
        builder.setTransactionalFlag(true);
        builder.setSystem(true);
        builder.setRecovery(RecoveryDescriptor.getDefault());
        builder.setSourceClassPath(sourceClass);
        builder.setConstructor(PreviewConstructor.getDefault());
        builder.setApplyMethod(new PreviewMethod(applyMethod, Collections.singletonList(applyMethodParam)));
        return builder.build();
    }
}
