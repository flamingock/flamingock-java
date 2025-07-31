/*
 * Copyright 2025 Flamingock (https://oss.flamingock.io)
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
 
package io.flamingock.internal.common.core.error.validation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class ValidationResult {
    private final String title;
    private final List<ValidationError> errors = new ArrayList<>();

    public ValidationResult(String title) {
        this.title = title;
    }

    public void add(ValidationError error) {
        errors.add(error);
    }

    public void addAll(Collection<ValidationError> errorList) {
        errors.addAll(errorList);
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public List<ValidationError> getErrors() {
        return errors;
    }

    public String formatMessage() {
        String body = errors.stream().map(ValidationError::getFormattedMessage).collect(Collectors.joining("\n\t- "));
        return String.format("%s:\n\t- %s", title, body);
    }
}

