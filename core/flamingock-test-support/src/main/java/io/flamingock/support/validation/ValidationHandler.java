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

import java.util.List;

public class ValidationHandler {

    private final List<Validator> validators;
    private final Throwable executionException;

    public ValidationHandler(List<Validator> validators) {
        this(validators, null);
    }

    public ValidationHandler(List<Validator> validators, Throwable executionException) {
        this.validators = validators;
        this.executionException = executionException;
    }


    public void validate() throws AssertionError {

        //TODO process validator and grab the potential validation errors in a AssertionError
        // we probably need another class for building the validation result


    }
}
