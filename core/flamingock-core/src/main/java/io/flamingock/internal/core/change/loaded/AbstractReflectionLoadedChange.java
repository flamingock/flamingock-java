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
package io.flamingock.internal.core.change.loaded;


import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import io.flamingock.internal.common.core.error.validation.ValidationError;
import io.flamingock.internal.common.core.change.RecoveryDescriptor;
import io.flamingock.internal.common.core.change.TargetSystemDescriptor;
import io.flamingock.internal.core.pipeline.loaded.stage.StageValidationContext;
import io.flamingock.internal.util.log.FlamingockLoggerFactory;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Abstract base class for loaded changes that use Java reflection to execute changes.
 * 
 * <p>This class serves as the foundation for both code-based and template-based changes,
 * providing common reflection-based execution capabilities. It handles the distinction between
 * the source file (where the change is defined) and the implementation class (where the logic resides).</p>
 * 
 * <h2>Change Types</h2>
 * <ul>
 *     <li><b>Code-based:</b> Changes defined directly in Java classes with {@code @Change} annotation</li>
 *     <li><b>Template-based:</b> Changes defined in YAML/JSON templates that reference implementation ChangeTemplate classes</li>
 * </ul>
 * 
 * <h2>Reflection Usage</h2>
 * <p>This class uses reflection to:</p>
 * <ul>
 *     <li>Instantiate change classes via constructor</li>
 *     <li>Invoke execution methods (annotated with {@code @Apply})</li>
 *     <li>Optionally invoke rollback methods (annotated with {@code @Rollback})</li>
 * </ul>
 * 
 * <h2>Subclass Responsibilities</h2>
 * <p>Concrete implementations must provide:</p>
 * <ul>
 *     <li>{@link #getConstructor()} - Constructor for instantiating the change</li>
 *     <li>{@link #getApplyMethod()} - Method to execute the change</li>
 *     <li>{@link #getRollbackMethod()} - Optional method for rollback operations</li>
 * </ul>
 * 
 * @author Antonio Perez
 * @since 6.0
 * @see AbstractLoadedChange
 */
public abstract class AbstractReflectionLoadedChange extends AbstractLoadedChange {
    // Lazy holder: keeps SLF4J off the build-time class-init chain. Subclasses are
    // explicitly initializeAtBuildTime'd by RegistrationFeature, which forces this
    // parent's <clinit>; touching org.slf4j.LoggerFactory there would pull SLF4J/Logback
    // into the image heap and break Spring Boot Native.
    private static final class LoggerHolder {
        static final Logger INSTANCE = FlamingockLoggerFactory.getLogger("ReflectionChange");
    }
    /**
     * Regex pattern for validating the order field in Changes.
     * The pattern requires at least 3 alphanumeric characters (a-z, A-Z, 0-9) anywhere in the string.
     * Can contain any other characters (underscores, dots, hyphens, etc.) along with the alphanumeric chars.
     * Examples: "abc", "123", "V1_2_3", "001abc", "release_1_2_beta", "20250925_01_migrationWithUnderscores"
     * Empty is not allowed
     */
    private final static String ORDER_REG_EXP = "^(?=(?:[^a-zA-Z0-9]*[a-zA-Z0-9]){3}).+$";
    private final static Pattern ORDER_PATTERN = Pattern.compile(ORDER_REG_EXP);
    /**
     * The source file name where this change is defined.
     * 
     * <p>This represents the original source of the change definition when available:</p>
     * <ul>
     *     <li><b>Template-based:</b> The YAML/JSON template file name (e.g., "create-users.yaml")</li>
     *     <li><b>Code-based:</b> {@code null} for now</li>
     * </ul>
     * 
     * <p>Note: This may differ from the {@link #implementationClass} in template-based scenarios
     * where the template references a separate implementation class.</p>
     */
    protected  final String fileName;

    /**
     * The Java class that contains the actual change execution logic.
     * 
     * <p>This class is used for reflection-based instantiation and method invocation:</p>
     * <ul>
     *     <li><b>Template-based:</b> A class implementing {@code ChangeTemplate} interface, 
     *         referenced by the template file</li>
     *     <li><b>Code-based:</b> The same class as indicated by {@link #fileName}, 
     *         containing {@code @Change} annotation</li>
     * </ul>
     * 
     * <p>This class must have:</p>
     * <ul>
     *     <li>A constructor accessible via {@link #getConstructor()}</li>
     *     <li>An execution method accessible via {@link #getApplyMethod()}</li>
     *     <li>Optionally, a rollback method accessible via {@link #getRollbackMethod()}</li>
     * </ul>
     */
    protected final Class<?> implementationClass;

    private final Constructor<?> constructor;

    public AbstractReflectionLoadedChange(String fileName,
                                          String id,
                                          String order,
                                          String author,
                                          Class<?> implementationClass,
                                          Constructor<?> constructor,
                                          boolean runAlways,
                                          Boolean transactionalFlag,
                                          boolean transactional,
                                          boolean system,
                                          TargetSystemDescriptor targetSystem,
                                          RecoveryDescriptor recovery,
                                          boolean legacy) {
        super(id, order, author, implementationClass.getName(), fileName, runAlways, transactionalFlag, transactional, system, targetSystem, recovery, legacy);
        this.constructor = constructor;
        this.fileName = fileName;
        this.implementationClass = implementationClass;
    }

    /**
     * Returns the source file name where this change is defined.
     * 
     * @return the file name for template-based changes, or {@code null} when unavailable
     * @see #fileName
     */
    public String getFileName() {
        return fileName;
    }

    /**
     * Returns the Java class containing the change execution logic.
     * 
     * @return the implementation class used for reflection-based execution
     * @see #implementationClass
     */
    public Class<?> getImplementationClass() {
        return implementationClass;
    }

    /**
     * Returns the constructor to be used for instantiating the change class.
     * 
     * <p>The constructor should be accessible and may require dependency injection
     * parameters based on the change's requirements.</p>
     * 
     * @return the constructor for creating instances of the implementation class
     */
    @Override
    public Constructor<?> getConstructor() {
        return constructor;
    }

    /**
     * Returns the method to be invoked for executing the change.
     * 
     * <p>This method typically:</p>
     * <ul>
     *     <li>Is annotated with {@code @Apply}</li>
     *     <li>Contains the main change logic</li>
     *     <li>May accept dependency-injected parameters</li>
     * </ul>
     * 
     * @return the method to execute the change logic
     */
    public abstract Method getApplyMethod();

    /**
     * Returns the optional method to be invoked for rolling back the change.
     * 
     * <p>This method, if present:</p>
     * <ul>
     *     <li>Is annotated with {@code @Rollback}</li>
     *     <li>Contains logic to undo the changes made by the execution method</li>
     *     <li>May accept dependency-injected parameters</li>
     * </ul>
     * 
     * @return an {@link Optional} containing the rollback method, or empty if no rollback is defined
     */
    public abstract Optional<Method> getRollbackMethod();


    @Override
    public List<ValidationError> getValidationErrors(StageValidationContext context) {
        List<ValidationError> errors = new ArrayList<>();
        final String entityType = "change";

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
        if (context.getSortType().isSorted()) {
            if (order == null || order.isEmpty()) {
                return Optional.of(new ValidationError("Change in a sorted stage but no order value was provided", id, "change"));
            }

            if (context.getSortType() == StageValidationContext.SortType.SEQUENTIAL_FORMATTED) {
                if (!ORDER_PATTERN.matcher(order).matches()) {
                    String message = String.format("Invalid order field format in change[%s]. Order must match pattern: %s", id, ORDER_REG_EXP);
                    return Optional.of(new ValidationError(message, id, "change"));
                }
            }

        } else if (order != null) {
            LoggerHolder.INSTANCE.warn("Change[{}] is in an auto-sorted stage but order value was provided - order will be ignored and managed automatically by Flamingock", id);

        }
        return Optional.empty();
    }

}
