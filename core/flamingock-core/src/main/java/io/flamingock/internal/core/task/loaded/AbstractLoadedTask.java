/*
 * Copyright 2023 Flamingock (https://oss.flamingock.io)
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

package io.flamingock.internal.core.task.loaded;

import io.flamingock.internal.common.core.error.validation.Validatable;
import io.flamingock.internal.common.core.error.validation.ValidationError;

import io.flamingock.internal.common.core.task.AbstractTaskDescriptor;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

public abstract class AbstractLoadedTask extends AbstractTaskDescriptor implements Validatable {


    /**
     * Regex pattern for validating the order field in ChangeUnits.
     * The pattern matches strings like "001", "999", "0010", "9999".
     * It requires at least 3 digits with leading zeros.
     * Empty is not allowed
     */
    private final static String ORDER_REG_EXP = "^\\d{3,}$";
    private final static Pattern ORDER_PATTERN = Pattern.compile(ORDER_REG_EXP);

    public AbstractLoadedTask(String id,
                              String order,
                              String source,
                              boolean runAlways,
                              boolean transactional,
                              boolean system) {
        super(id, order, source, runAlways, transactional, system);
    }

    public abstract Constructor<?> getConstructor();

    public abstract Method getExecutionMethod();

    public abstract Optional<Method> getRollbackMethod();


    @Override
    public List<ValidationError> getValidationErrors() {
        List<ValidationError> errors = new ArrayList<>();
        final String entityType = "task";

        // Validate ID is not null or empty
        if (id == null || id.trim().isEmpty()) {
            errors.add(new ValidationError("ID cannot be null or empty", "unknown", entityType));
        }


        getOrder().ifPresent(orderValue -> {
            if (!ORDER_PATTERN.matcher(orderValue).matches()) {
                String message = String.format("Invalid order field format in changeUnit[%s]. Order must match pattern: %s", id, ORDER_REG_EXP);
                errors.add(new ValidationError(message, id, entityType
                ));
            }
        });


        // Validate source is not null or empty
        if (source == null || source.trim().isEmpty()) {
            errors.add(new ValidationError("Source cannot be null or empty", id, entityType));
        }

        return errors;
    }



}
