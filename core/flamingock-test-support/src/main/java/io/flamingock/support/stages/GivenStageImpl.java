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
package io.flamingock.support.stages;

import io.flamingock.internal.core.builder.BuilderAccessor;
import io.flamingock.support.domain.AuditEntryDefinition;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GivenStageImpl implements GivenStage {

    private final BuilderAccessor builderAccessor;
    private final List<AuditEntryDefinition> existingAudit = new ArrayList<>();

    public GivenStageImpl(BuilderAccessor builderAccessor) {
        this.builderAccessor = builderAccessor;
    }

    @Override
    public GivenStage andExistingAudit(AuditEntryDefinition... definitions) {
        if (definitions != null) {
            existingAudit.addAll(Arrays.asList(definitions));
        }
        return this;
    }

    @Override
    public WhenStage whenRun() {
        return new WhenStageImpl(builderAccessor);
    }

    /**
     * Returns the list of existing audit definitions.
     *
     * @return the existing audit definitions
     */
    public List<AuditEntryDefinition> getExistingAudit() {
        return existingAudit;
    }
}
