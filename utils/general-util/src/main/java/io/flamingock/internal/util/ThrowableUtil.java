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
package io.flamingock.internal.util;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.stream.Collectors;

public final class ThrowableUtil {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    // límite global de caracteres en la salida JSON
    private static final int MAX_CHARS = 20000;

    private ThrowableUtil() {
    }

    public static String messageOf(Throwable t) {
        String msg = t.getMessage();
        return (msg != null && !msg.isEmpty()) ? msg : t.getClass().getName();
    }

    public static String serialize(Throwable e) {
        return serialize(e, 100);
    }

    public static String serialize(Throwable e, int maxFrames) {
        if (e == null) {
            return "";
        }

        Map<String, Object> errorInfo = new LinkedHashMap<>();
        errorInfo.put("type", e.getClass().getName());
        errorInfo.put("message", Objects.toString(e.getMessage(), ""));

        Throwable root = getRootCause(e);
        if (root != null && root != e) {
            Map<String, Object> rootInfo = new LinkedHashMap<>();
            rootInfo.put("type", root.getClass().getName());
            rootInfo.put("message", Objects.toString(root.getMessage(), ""));
            errorInfo.put("rootCause", rootInfo);
        }

        List<Map<String, Object>> frames = Arrays.stream(e.getStackTrace())
                .limit(maxFrames)
                .map(frame -> {
                    Map<String, Object> f = new LinkedHashMap<>();
                    f.put("className", frame.getClassName());
                    f.put("methodName", frame.getMethodName());
                    f.put("fileName", frame.getFileName());
                    f.put("lineNumber", frame.getLineNumber());
                    return f;
                })
                .collect(Collectors.toList());

        errorInfo.put("stackTrace", frames);

        try {
            String json = MAPPER.writeValueAsString(errorInfo);
            if (json.length() > MAX_CHARS) {
                return json.substring(0, MAX_CHARS) + "...";
            }
            return json;
        } catch (Exception ex) {
            return "{\"type\":\"" + e.getClass().getName() + "\",\"serializationError\":\"" + ex.getMessage() + "\"}";
        }
    }

    private static Throwable getRootCause(Throwable e) {
        Throwable cause = e.getCause();
        if (cause == null) return null;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }
}
