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
