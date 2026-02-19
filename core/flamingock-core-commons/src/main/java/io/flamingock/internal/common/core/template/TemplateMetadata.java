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
package io.flamingock.internal.common.core.template;

import java.util.Objects;

/**
 * Metadata describing a change template discovered during annotation processing.
 *
 * <p>This class holds compile-time information about templates that is serialized
 * to {@code FlamingockMetadata} and used at runtime to initialize the
 * {@link ChangeTemplateManager}.
 *
 * <p>Templates are discovered via two mechanisms:
 * <ul>
 *   <li>Annotation processing: Local templates with {@code @ChangeTemplate} annotation</li>
 *   <li>File-based: External templates listed in {@code META-INF/flamingock/templates}</li>
 * </ul>
 */
public class TemplateMetadata {

    /**
     * The unique identifier from {@code @ChangeTemplate.id()}.
     * Used in YAML files to reference the template.
     */
    private String id;

    /**
     * Whether this template processes multiple steps ({@code @ChangeTemplate.multiStep()}).
     */
    private boolean multiStep;

    /**
     * The fully qualified class name of the template for {@code Class.forName()}.
     */
    private String fullyQualifiedClassName;

    /**
     * Whether this template was discovered via a file-based registration
     * ({@code META-INF/flamingock/templates}). This is operational metadata
     * and does not affect identity ({@code equals}/{@code hashCode}).
     */
    private boolean fileRegistered;

    /**
     * Default constructor for Jackson deserialization.
     */
    public TemplateMetadata() {
    }

    /**
     * Creates a new TemplateMetadata instance with {@code fileRegistered = false}.
     *
     * @param id                     the unique template identifier
     * @param multiStep              whether the template processes multiple steps
     * @param fullyQualifiedClassName the fully qualified class name
     */
    public TemplateMetadata(String id, boolean multiStep, String fullyQualifiedClassName) {
        this(id, multiStep, fullyQualifiedClassName, false);
    }

    /**
     * Creates a new TemplateMetadata instance.
     *
     * @param id                     the unique template identifier
     * @param multiStep              whether the template processes multiple steps
     * @param fullyQualifiedClassName the fully qualified class name
     * @param fileRegistered         whether this template was discovered via file registration
     */
    public TemplateMetadata(String id, boolean multiStep, String fullyQualifiedClassName, boolean fileRegistered) {
        this.id = id;
        this.multiStep = multiStep;
        this.fullyQualifiedClassName = fullyQualifiedClassName;
        this.fileRegistered = fileRegistered;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public boolean isMultiStep() {
        return multiStep;
    }

    public void setMultiStep(boolean multiStep) {
        this.multiStep = multiStep;
    }

    public String getFullyQualifiedClassName() {
        return fullyQualifiedClassName;
    }

    public void setFullyQualifiedClassName(String fullyQualifiedClassName) {
        this.fullyQualifiedClassName = fullyQualifiedClassName;
    }

    public boolean isFileRegistered() {
        return fileRegistered;
    }

    public void setFileRegistered(boolean fileRegistered) {
        this.fileRegistered = fileRegistered;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TemplateMetadata that = (TemplateMetadata) o;
        return multiStep == that.multiStep &&
                Objects.equals(id, that.id) &&
                Objects.equals(fullyQualifiedClassName, that.fullyQualifiedClassName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, multiStep, fullyQualifiedClassName);
    }

    @Override
    public String toString() {
        return "TemplateMetadata{" +
                "id='" + id + '\'' +
                ", multiStep=" + multiStep +
                ", fullyQualifiedClassName='" + fullyQualifiedClassName + '\'' +
                ", fileRegistered=" + fileRegistered +
                '}';
    }
}
