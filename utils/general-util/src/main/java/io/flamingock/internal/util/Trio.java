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

import java.util.Objects;

public class Trio<A, B, C> {
    private final A first;
    private final B second;
    private final C third;


    public Trio(A first) {
        this(first, null, null);
    }

    public Trio(A first, B second) {
        this(first, second, null);
    }

    public Trio(A first, B second, C third) {
        this.first = first;
        this.second = second;
        this.third = third;
    }

    public A getFirst() {
        return first;
    }

    public B getSecond() {
        return second;
    }

    public C getThird() {
        return third;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Trio)) return false;
        Trio<?, ?, ?> trio = (Trio<?, ?, ?>) o;
        return Objects.equals(first, trio.first)
                && Objects.equals(second, trio.second)
                && Objects.equals(third, trio.third);
    }

    @Override
    public int hashCode() {
        return Objects.hash(first, second, third);
    }

    @Override
    public String toString() {
        return "Trio{first=" + first + ", second=" + second + ", third=" + third + "}";
    }
}

