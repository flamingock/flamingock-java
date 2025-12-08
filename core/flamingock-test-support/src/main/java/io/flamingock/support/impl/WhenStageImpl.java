package io.flamingock.support.impl;

import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.support.ThenStage;
import io.flamingock.support.WhenStage;
import io.flamingock.support.domain.AuditEntryExpectation;

import java.util.List;
import java.util.function.Consumer;

public class WhenStageImpl implements WhenStage {

    WhenStageImpl() {}

    @Override
    public ThenStage thenExpectAuditSequenceStrict(AuditEntryExpectation... expectations) {
        return new ThenStageImpl()
                .andExpectAuditSequenceStrict(expectations);
    }

    @Override
    public ThenStage thenExpectException(Class<? extends Throwable> exceptionClass, Consumer<Throwable> validator) {
        return new ThenStageImpl()
                .andExpectException(exceptionClass, validator);
    }

}
