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

    /**
     * Empty constructor for Jackson deserialization.
     */
    public BuilderProviderInfo() {
    }

    public BuilderProviderInfo(String className, String methodName) {
        this.className = className;
        this.methodName = methodName;
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
                '}';
    }
}
