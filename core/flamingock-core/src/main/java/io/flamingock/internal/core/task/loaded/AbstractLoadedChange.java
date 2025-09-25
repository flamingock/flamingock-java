/*
 * Copyright 2023 Flamingock (https://www.flamingock.io)
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

import io.flamingock.api.annotations.Categories;
import io.flamingock.api.annotations.FlamingockConstructor;
import io.flamingock.api.task.ChangeCategory;
import io.flamingock.internal.common.core.error.FlamingockException;
import io.flamingock.internal.common.core.error.validation.ValidationError;
import io.flamingock.internal.core.pipeline.loaded.stage.StageValidationContext;
import io.flamingock.internal.util.ReflectionUtil;
import io.flamingock.internal.common.core.task.RecoveryDescriptor;
import io.flamingock.internal.common.core.task.TargetSystemDescriptor;
import org.jetbrains.annotations.NotNull;
import io.flamingock.internal.util.log.FlamingockLoggerFactory;
import org.slf4j.Logger;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public abstract class AbstractLoadedChange extends AbstractReflectionLoadedTask {

    private static final Logger logger = FlamingockLoggerFactory.getLogger("Change");
    /**
     * Regex pattern for validating the order field in Changes.
     * The pattern matches strings like "001", "999", "0010", "9999".
     * It requires at least 3 digits with leading zeros.
     * Empty is not allowed
     */
    private final static String ORDER_REG_EXP = "^\\d{3,}$";
    private final static Pattern ORDER_PATTERN = Pattern.compile(ORDER_REG_EXP);
    private final Set<ChangeCategory> categories;


    protected AbstractLoadedChange(String fileName,
                                   String id,
                                   String order,
                                   String author,
                                   Class<?> implementationClass,
                                   boolean runAlways,
                                   boolean transactional,
                                   boolean system,
                                   TargetSystemDescriptor targetSystem,
                                   RecoveryDescriptor recovery) {
        super(fileName, id, order, author, implementationClass, runAlways, transactional, system, targetSystem, recovery);

        this.categories = ReflectionUtil.findAllAnnotations(implementationClass, Categories.class).stream()
                .map(Categories::value)
                .map(Arrays::asList)
                .flatMap(List::stream)
                .collect(Collectors.toSet());
    }

    @Override
    public Constructor<?> getConstructor() {
        try {
            return ReflectionUtil.getConstructorWithAnnotationPreference(getImplementationClass(), FlamingockConstructor.class);
        } catch (ReflectionUtil.MultipleAnnotatedConstructorsFound ex) {
            throw new FlamingockException("Found multiple constructors for class[%s] annotated with %s." +
                    " Annotate the one you want Flamingock to use to instantiate your change",
                    getSource(),
                    FlamingockConstructor.class.getName());
        } catch (ReflectionUtil.MultipleConstructorsFound ex) {
            throw new FlamingockException("Found multiple constructors, please provide at least one  for class[%s].\n" +
                    "When more than one constructor, exactly one of them must be annotated. And it will be taken as default "
                    , FlamingockConstructor.class.getSimpleName()
                    , getSource()
            );
        } catch (ReflectionUtil.ConstructorNotFound ex) {
            throw new FlamingockException("Cannot find a valid constructor for class[%s]", getSource());
        }
    }

    @Override
    public boolean hasCategory(ChangeCategory property) {
        return categories.contains(property);
    }

    @Override
    public List<ValidationError> getValidationErrors(StageValidationContext context) {
        List<ValidationError> errors = new ArrayList<>();
        final String entityType = "task";

        // Validate ID is not null or empty
        if (id == null || id.trim().isEmpty()) {
            errors.add(new ValidationError("ID cannot be null or empty", "unknown", entityType));
        }

        getOrderError(context).ifPresent(errors::add);


        // Validate source is not null or empty
        if (source == null || source.trim().isEmpty()) {
            errors.add(new ValidationError("Source cannot be null or empty", id, entityType));
        }

        return errors;
    }


    @NotNull
    private Optional<ValidationError> getOrderError(StageValidationContext context) {
        String order = getOrder().orElse(null);
        if(context.getSortType().isSorted()) {
            if (order == null || order.isEmpty()) {
                return Optional.of(new ValidationError("Change in a sorted stage but no order value was provided", id, "change"));
            }

            if(context.getSortType() == StageValidationContext.SortType.SEQUENTIAL_FORMATTED) {
                if (!ORDER_PATTERN.matcher(order).matches()) {
                    String message = String.format("Invalid order field format in change[%s]. Order must match pattern: %s", id, ORDER_REG_EXP);
                    return Optional.of(new ValidationError(message, id, "task"));
                }
            }

        } else if(order != null) {
            logger.warn("Change[{}] is in an auto-sorted stage but order value was provided - order will be ignored and managed automatically by Flamingock", id);

        }
        return Optional.empty();
    }
}
