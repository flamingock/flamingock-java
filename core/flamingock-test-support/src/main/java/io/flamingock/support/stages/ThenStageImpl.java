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

import io.flamingock.support.context.TestContext;
import io.flamingock.support.domain.AuditEntryDefinition;
import io.flamingock.support.precondition.PreconditionInserter;
import io.flamingock.support.validation.ValidationHandler;
import io.flamingock.support.validation.Validator;
import io.flamingock.support.validation.ValidatorFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

final class ThenStageImpl implements ThenStage {

    private final List<Validator> validators = new ArrayList<>();
    private final ValidatorFactory validatorFactory;
    private final TestContext testContext;

    ThenStageImpl(TestContext testContext) {
        this.testContext = testContext;
        validatorFactory = new ValidatorFactory(testContext.getAuditReader());
    }

    @Override
    public ThenStage andExpectAuditSequenceStrict(AuditEntryDefinition... definitions) {
        validators.add(validatorFactory.getAuditSeqStrictValidator(definitions));
        return this;
    }

    @Override
    public ThenStage andExpectException(Class<? extends Throwable> exceptionClass, Consumer<Throwable> exceptionConsumer) {
        validators.add(validatorFactory.getExceptionValidator(exceptionClass, exceptionConsumer));
        return this;
    }

    @Override
    public void verify() throws AssertionError {
        // Insert preconditions first
        PreconditionInserter preconditionInserter = new PreconditionInserter(testContext.getAuditWriter());
        preconditionInserter.insert(testContext.getPreconditions());

        ValidationHandler validationHandler;
        try {
            testContext.run();
            validationHandler = new ValidationHandler(validators);

        } catch (Throwable actualException) {
            validationHandler = new ValidationHandler(validators, actualException);

        }

        validationHandler.validate();
    }
}
