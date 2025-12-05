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
package io.flamingock.internal.core.builder;

import java.util.function.Consumer;
import java.util.List;
import io.flamingock.internal.common.core.audit.AuditEntry;

class ThenStageImpl implements ThenStage {

    private final WhenStageImpl when;

    ThenStageImpl(WhenStageImpl when) {
        this.when = when;
    }

    @Override
    public ThenStage thenExpectAuditSequenceStrict(AuditEntryExpectation... expectations) {
        when.thenExpectAuditSequenceStrict(expectations);
        return this;
    }

    @Override
    public ThenStage thenExpectException(Class<? extends Throwable> exceptionClass, Consumer<Throwable> validator) {
        when.thenExpectException(exceptionClass, validator);
        return this;
    }

    @Override
    public ThenStage thenInspectAuditEntries(Consumer<List<AuditEntry>> validator) {
        when.thenInspectAuditEntries(validator);
        return this;
    }

    @Override
    public void verify() {
        when.verify();
    }
}
