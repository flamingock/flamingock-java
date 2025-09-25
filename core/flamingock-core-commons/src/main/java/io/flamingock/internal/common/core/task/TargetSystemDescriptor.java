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
package io.flamingock.internal.common.core.task;

import java.util.Objects;

/**
 * Descriptor that references a target system from a change or task.
 * This is a reference to link the change to a target system defined elsewhere,
 * not the actual target system definition itself.
 */
public class TargetSystemDescriptor {

    private String id;

    /**
     * Default constructor for Jackson deserialization.
     */
    public TargetSystemDescriptor() {
    }

    /**
     * Creates a new target system descriptor with the specified ID.
     *
     * @param id the target system ID
     */
    public TargetSystemDescriptor(String id) {
        this.id = id;
    }

    /**
     * Factory method to create a TargetSystemDescriptor from an ID.
     * Returns null if the ID is null.
     *
     * @param id the target system ID
     * @return a new TargetSystemDescriptor, or null if id is null
     */
    public static TargetSystemDescriptor fromId(String id) {
        return id != null ? new TargetSystemDescriptor(id) : null;
    }

    /**
     * Gets the target system ID.
     *
     * @return the target system ID
     */
    public String getId() {
        return id;
    }

    /**
     * Sets the target system ID.
     *
     * @param id the target system ID
     */
    public void setId(String id) {
        this.id = id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TargetSystemDescriptor that = (TargetSystemDescriptor) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "TargetSystemDescriptor{" +
                "id='" + id + '\'' +
                '}';
    }
}