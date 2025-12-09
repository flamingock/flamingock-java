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

import io.flamingock.support.validation.error.ValidationError;
import io.flamingock.support.validation.error.ValidationResult;

import java.util.List;

/**
 * Formats validation errors into a human-readable message for display
 * in assertion errors.
 *
 * <p>The formatter groups errors by validator and presents them in a
 * clear, structured format.</p>
 */
public class ValidationErrorFormatter {

    private static final String HEADER = "Flamingock Test Verification Failed";
    private static final String BULLET = "  • ";
    private static final String NEWLINE = "\n";

    /**
     * Formats a list of validation failures into a human-readable message.
     *
     * @param failures the list of validation results that contain errors
     * @return a formatted error message
     */
    public String format(List<ValidationResult> failures) {
        StringBuilder sb = new StringBuilder();
        sb.append(HEADER).append(NEWLINE);

        for (ValidationResult result : failures) {
            sb.append(NEWLINE);
            sb.append("[").append(result.getValidatorName()).append("]").append(NEWLINE);

            for (ValidationError error : result.getErrors()) {
                sb.append(BULLET).append(error.formatMessage()).append(NEWLINE);
            }
        }

        return sb.toString().trim();
    }
}
