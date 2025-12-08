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
package io.flamingock.support.validation;

import io.flamingock.internal.core.builder.BuilderAccessor;
import io.flamingock.support.domain.AuditEntryExpectation;
import io.flamingock.support.validation.impl.AuditSequenceStrictValidator;
import io.flamingock.support.validation.impl.DefaultExceptionValidator;

import java.util.function.Consumer;

public class ValidatorFactory {

    private final BuilderAccessor builderAccessor;

    public ValidatorFactory(BuilderAccessor builderAccessor) {
        this.builderAccessor = builderAccessor;
    }

    public Validator getAuditSeqStrictValidator(AuditEntryExpectation... expectations) {
        return new AuditSequenceStrictValidator(builderAccessor.getAuditStore(), expectations);
    }

    public Validator getExceptionValidator(Class<? extends Throwable> exceptionClass, Consumer<Throwable> exceptionConsumer) {
        return new DefaultExceptionValidator(exceptionClass, exceptionConsumer);
    }
}
