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
package io.flamingock.support.context;

import io.flamingock.internal.common.core.audit.AuditReader;
import io.flamingock.internal.common.core.audit.AuditWriter;
import io.flamingock.internal.core.builder.BuilderAccessor;
import io.flamingock.support.domain.AuditEntryDefinition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Context object that carries test execution data through the BDD stages.
 *
 * <p>This class encapsulates the builder accessor and preconditions, providing
 * controlled access to only what each stage needs. It follows the principle of
 * exposing behavior, not implementation details.</p>
 *
 * <p>Key design decisions:</p>
 * <ul>
 *   <li>No getter for {@code BuilderAccessor} - exposes only what's needed</li>
 *   <li>Separate {@code getAuditReader()} and {@code getAuditWriter()} methods</li>
 *   <li>Immutable preconditions list (defensive copy)</li>
 * </ul>
 */
public class TestContext {

    private final BuilderAccessor builderAccessor;
    private final List<AuditEntryDefinition> preconditions;

    /**
     * Creates a new test context with the given builder accessor and preconditions.
     *
     * @param builderAccessor the builder accessor for running and accessing audit store
     * @param preconditions the list of audit entry definitions to insert as preconditions
     */
    public TestContext(BuilderAccessor builderAccessor, List<AuditEntryDefinition> preconditions) {
        this.builderAccessor = builderAccessor;
        this.preconditions = preconditions != null
                ? new ArrayList<>(preconditions)
                : new ArrayList<>();
    }

    /**
     * Returns the audit reader for reading audit entries.
     *
     * @return the audit reader
     */
    public AuditReader getAuditReader() {
        return builderAccessor.getAuditStore().getPersistence();
    }

    /**
     * Returns the audit writer for writing audit entries.
     *
     * @return the audit writer
     */
    public AuditWriter getAuditWriter() {
        return builderAccessor.getAuditStore().getPersistence();
    }

    /**
     * Runs the change runner by building and executing it.
     */
    public void run() {
        builderAccessor.run();
    }

    /**
     * Returns an unmodifiable view of the preconditions.
     *
     * @return the preconditions list
     */
    public List<AuditEntryDefinition> getPreconditions() {
        return Collections.unmodifiableList(preconditions);
    }
}
