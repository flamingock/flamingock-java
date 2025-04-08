package io.flamingock.commons.utils;

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

