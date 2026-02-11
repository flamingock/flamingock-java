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
package io.flamingock.internal.common.core.metadata;

/**
 * Holds information about the @FlamingockCliBuilder annotated method.
 * Used for serialization to/from metadata.json.
 */
public class BuilderProviderInfo {

    private String className;
    private String methodName;
    private boolean acceptsArgs;

    /**
     * Empty constructor for Jackson deserialization.
     */
    public BuilderProviderInfo() {
    }

    /**
     * Constructor for backward compatibility (no args).
     */
    public BuilderProviderInfo(String className, String methodName) {
        this(className, methodName, false);
    }

    /**
     * Full constructor with acceptsArgs flag.
     *
     * @param className   the fully qualified class name containing the builder method
     * @param methodName  the method name
     * @param acceptsArgs true if the method accepts String[] args parameter
     */
    public BuilderProviderInfo(String className, String methodName, boolean acceptsArgs) {
        this.className = className;
        this.methodName = methodName;
        this.acceptsArgs = acceptsArgs;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    /**
     * Returns true if the builder method accepts String[] args parameter.
     *
     * @return true if method signature is: methodName(String[] args)
     */
    public boolean isAcceptsArgs() {
        return acceptsArgs;
    }

    public void setAcceptsArgs(boolean acceptsArgs) {
        this.acceptsArgs = acceptsArgs;
    }

    /**
     * Validates that both class and method names are present.
     * @return true if both fields are non-null and non-empty
     */
    public boolean isValid() {
        return className != null && !className.isEmpty()
            && methodName != null && !methodName.isEmpty();
    }

    @Override
    public String toString() {
        return "BuilderProviderInfo{" +
                "className='" + className + '\'' +
                ", methodName='" + methodName + '\'' +
                ", acceptsArgs=" + acceptsArgs +
                '}';
    }
}
