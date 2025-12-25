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

import io.flamingock.internal.core.runner.Runner;
import io.flamingock.support.context.TestContext;
import io.flamingock.support.domain.AuditEntryDefinition;
import io.flamingock.support.precondition.PreconditionInserter;
import io.flamingock.support.validation.ValidationHandler;
import io.flamingock.support.validation.ValidatorArgs;
import io.flamingock.support.validation.impl.AuditFinalStateSequenceValidator;
import io.flamingock.support.validation.impl.DefaultExceptionValidator;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.Arrays;
import java.util.Collections;

final class ThenStageImpl implements ThenStage {

    private final List<ValidatorArgs> validators = new ArrayList<>();
    private final TestContext testContext;

    ThenStageImpl(TestContext testContext) {
        this.testContext = testContext;
    }

    @Override
    public ThenStage andExpectAuditFinalStateSequence(AuditEntryDefinition... definitions) {
        List<AuditEntryDefinition> definitionsList = definitions != null ? Arrays.asList(definitions) : Collections.<AuditEntryDefinition>emptyList();
        validators.add(new AuditFinalStateSequenceValidator.Args(definitionsList));
        return this;
    }

    @Override
    public ThenStage andExpectException(Class<? extends Throwable> exceptionClass, Consumer<Throwable> exceptionConsumer) {
        validators.add(new DefaultExceptionValidator.Args(exceptionClass, exceptionConsumer));
        return this;
    }

    @Override
    public void verify() throws AssertionError {
        Throwable exception = null;
        try {
            Runner runner = testContext.build();
            PreconditionInserter preconditionInserter = new PreconditionInserter(testContext.getAuditWriter());
            preconditionInserter.insert(testContext.getPreconditions());
            runner.run();

        } catch (Throwable actualException) {
            exception = actualException;

        }

        new ValidationHandler(testContext, validators, exception).validate();
        testContext.cleanUp();
    }
}
