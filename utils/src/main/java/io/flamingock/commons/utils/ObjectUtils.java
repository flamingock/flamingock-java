package io.flamingock.commons.utils;

import com.fasterxml.jackson.databind.ObjectWriter;

import java.util.function.Function;

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
