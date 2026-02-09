/*
 * Copyright 2026 Flamingock (https://www.flamingock.io)
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
package io.flamingock.cli.executor.output;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Formats objects as JSON for console output.
 */
public class JsonFormatter {

    private static final ObjectMapper MAPPER;

    static {
        MAPPER = new ObjectMapper();
        MAPPER.registerModule(new JavaTimeModule());
        MAPPER.enable(SerializationFeature.INDENT_OUTPUT);
        MAPPER.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    private JsonFormatter() {
    }

    /**
     * Prints an object as pretty-printed JSON.
     *
     * @param object the object to print
     */
    public static void print(Object object) {
        if (object == null) {
            System.out.println("null");
            return;
        }
        try {
            String json = MAPPER.writeValueAsString(object);
            System.out.println(json);
        } catch (JsonProcessingException e) {
            System.err.println("Error serializing to JSON: " + e.getMessage());
        }
    }

    /**
     * Converts an object to a JSON string.
     *
     * @param object the object to convert
     * @return JSON string representation
     */
    public static String toJson(Object object) {
        if (object == null) {
            return "null";
        }
        try {
            return MAPPER.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }
}
