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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import io.flamingock.internal.common.core.audit.AuditEntry;

class WhenStageImpl implements WhenStage {

    final GivenStageImpl given;
    final List<AuditEntryExpectation> auditExpectations = new ArrayList<>();
    final List<ExceptionExpectation> exceptionExpectations = new ArrayList<>();
    final List<Consumer<List<AuditEntry>>> auditEntryValidators = new ArrayList<>();
    private boolean verified = false;

    WhenStageImpl(GivenStageImpl given) {
        this.given = given;
    }

    @Override
    public ThenStage thenExpectAuditSequenceStrict(AuditEntryExpectation... expectations) {
        if (expectations != null) {
            for (AuditEntryExpectation e : expectations) {
                auditExpectations.add(e);
            }
        }
        return new ThenStageImpl(this);
    }

    @Override
    public ThenStage thenExpectException(Class<? extends Throwable> exceptionClass, Consumer<Throwable> validator) {
        exceptionExpectations.add(new ExceptionExpectation(exceptionClass, validator));
        return new ThenStageImpl(this);
    }

    @Override
    public ThenStage thenInspectAuditEntries(Consumer<List<AuditEntry>> validator) {
        if (validator != null) {
            auditEntryValidators.add(validator);
        }
        return new ThenStageImpl(this);
    }

    @Override
    public void verify() {
        this.verified = true;
    }

    static final class ExceptionExpectation {
        final Class<? extends Throwable> exceptionClass;
        final java.util.function.Consumer<Throwable> validator;
        ExceptionExpectation(Class<? extends Throwable> exceptionClass, java.util.function.Consumer<Throwable> validator) {
            this.exceptionClass = exceptionClass;
            this.validator = validator;
        }
    }
}
