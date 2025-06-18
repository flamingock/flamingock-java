package io.flamingock.internal.util;

public class ObjectUtils {

    private ObjectUtils() {
        throw new AssertionError("Instances of ObjectUtils not allowed");
    }

    public static <T> T requireNonNull(T obj) {
        if (obj == null)
            throw new NullPointerException();
        return obj;
    }

}
