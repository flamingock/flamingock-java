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

import io.flamingock.internal.common.core.audit.AuditReader;
import io.flamingock.support.domain.AuditEntryDefinition;
import io.flamingock.support.validation.impl.AuditSequenceStrictValidator;
import io.flamingock.support.validation.impl.DefaultExceptionValidator;

import java.util.List;
import java.util.function.Consumer;

public class ValidatorFactory {

    private final AuditReader auditReader;

    public ValidatorFactory(AuditReader auditReader) {
        this.auditReader = auditReader;
    }

    public Validator getAuditSeqStrictValidator(List<AuditEntryDefinition> definitions) {
        return new AuditSequenceStrictValidator(auditReader, definitions);
    }

    public Validator getExceptionValidator(Class<? extends Throwable> exceptionClass, Consumer<Throwable> exceptionConsumer) {
        return new DefaultExceptionValidator(exceptionClass, exceptionConsumer);
    }

    public Validator getValidator(ValidatorArgs args) {
        if (args instanceof AuditSequenceStrictValidator.Args) {
            AuditSequenceStrictValidator.Args a = (AuditSequenceStrictValidator.Args) args;
            return new AuditSequenceStrictValidator(auditReader, a.getExpectations());
        }

        if (args instanceof DefaultExceptionValidator.Args) {
            DefaultExceptionValidator.Args a = (DefaultExceptionValidator.Args) args;
            return new DefaultExceptionValidator(a.getExceptionClass(), a.getExceptionConsumer());
        }

        throw new IllegalArgumentException("Unknown ValidatorArgs type: " + (args != null ? args.getClass() : "null"));
    }
}
