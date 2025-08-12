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
package io.flamingock.internal.util;

/**
 * Simple mutable holder for a value.
 * <p>
 * This class is <strong>not</strong> thread-safe. It is intended for use in scenarios
 * such as capturing and mutating a variable from within a lambda expression.
 *
 * @param <T> the type of value being wrapped
 */
public final class Wrapper<T> {
    private T value;

    public Wrapper() {
    }

    public Wrapper(T value) {
        this.value = value;
    }

    /**
     * Returns the current value.
     */
    public T getValue() {
        return value;
    }

    /**
     * Sets a new value.
     */
    public void setValue(T value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return String.valueOf(value);
    }
}