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


import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import io.flamingock.internal.common.core.task.RecoveryDescriptor;
import io.flamingock.internal.common.core.task.TargetSystemDescriptor;
import java.util.Optional;

/**
 * Abstract base class for loaded tasks that use Java reflection to execute changes.
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
 * @see AbstractLoadedTask
 */
public abstract class AbstractReflectionLoadedTask extends AbstractLoadedTask {

    /**
     * The source file name where this change is defined.
     * 
     * <p>This represents the original source of the change definition:</p>
     * <ul>
     *     <li><b>Template-based:</b> The YAML/JSON template file name (e.g., "create-users.yaml")</li>
     *     <li><b>Code-based:</b> The Java class name containing the {@code @Change} annotation</li>
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

    public AbstractReflectionLoadedTask(String fileName,
                                        String id,
                                        String order,
                                        String author,
                                        Class<?> implementationClass,
                                        boolean runAlways,
                                        boolean transactional,
                                        boolean system,
                                        TargetSystemDescriptor targetSystem,
                                        RecoveryDescriptor recovery,
                                        boolean legacy) {
        super(id, order, author, implementationClass.getName(), runAlways, transactional, system, targetSystem, recovery, legacy);
        this.fileName = fileName;
        this.implementationClass = implementationClass;
    }

    /**
     * Returns the source file name where this change is defined.
     * 
     * @return the file name (template file for template-based, class name for code-based)
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
    public abstract Constructor<?> getConstructor();

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

}
