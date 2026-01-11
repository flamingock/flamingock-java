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
package io.flamingock.template.mongodb.mapper;

import com.mongodb.client.model.Collation;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.bson.conversions.Bson;

import java.util.List;
import java.util.Map;

public final class MapperUtil {
    private MapperUtil(){}


    public static Boolean getBoolean(Map<String, Object> options, String key) {
        Object value = options.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        } else {
            throw new IllegalArgumentException(String.format("field[%s] should be Boolean", key));
        }
    }

    public static String getString(Map<String, Object> options, String key) {
        Object value = options.get(key);
        if (value instanceof String) {
            return (String) value;
        } else {
            throw new IllegalArgumentException(String.format("field[%s] should be String", key));
        }
    }

    public static Integer getInteger(Map<String, Object> options, String key) {
        Object value = options.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        } else {
            throw new IllegalArgumentException(String.format("field[%s] should be Integer", key));
        }
    }

    public static Long getLong(Map<String, Object> options, String key) {
        Object value = options.get(key);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        } else {
            throw new IllegalArgumentException(String.format("field[%s] should be Long", key));
        }
    }

    public static Double getDouble(Map<String, Object> options, String key) {
        Object value = options.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        } else {
            throw new IllegalArgumentException(String.format("field[%s] should be Double", key));
        }
    }

    public static Bson getBson(Map<String, Object> options, String key) {
        Object value = options.get(key);
        if (value instanceof Bson) {
            return (Bson) value;
        } else if (value instanceof Map) {
            return toBsonDocument((Map<String, Object>) value);
        } else {
            throw new IllegalArgumentException(String.format("field[%s] should be Bson", key));
        }
    }

    public static Collation getCollation(Map<String, Object> options, String key) {
        Object value = options.get(key);
        if (value instanceof Collation) {
            return (Collation) value;
        } else {
            throw new IllegalArgumentException(String.format("field[%s] should be Collation", key));
        }
    }

    // Recursively converts a Map<String, Object> to BsonDocument
    public static BsonDocument toBsonDocument(Map<String, Object> map) {
        BsonDocument document = new BsonDocument();
        map.forEach((key, value) -> document.append(key, toBsonValue(value)));
        return document;
    }

    // Converts Java types into BSON types
    @SuppressWarnings("unchecked")
    public static BsonValue toBsonValue(Object value) {
        if (value == null) {
            return org.bson.BsonNull.VALUE;
        } else if (value instanceof String) {
            return new org.bson.BsonString((String) value);
        } else if (value instanceof Integer) {
            return new org.bson.BsonInt32((Integer) value);
        } else if (value instanceof Long) {
            return new org.bson.BsonInt64((Long) value);
        } else if (value instanceof Double) {
            return new org.bson.BsonDouble((Double) value);
        } else if (value instanceof Boolean) {
            return new org.bson.BsonBoolean((Boolean) value);
        } else if (value instanceof List) {
            return toBsonArray((List<?>) value);
        } else if (value instanceof Map) {
            return toBsonDocument((Map<String, Object>) value);
        }
        throw new IllegalArgumentException("Unsupported BSON type: " + value.getClass().getSimpleName());
    }

    // Converts a List to BsonArray
    public static BsonArray toBsonArray(List<?> list) {
        BsonArray array = new BsonArray();
        for (Object item : list) {
            array.add(toBsonValue(item));
        }
        return array;
    }
}
