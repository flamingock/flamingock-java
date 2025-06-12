package io.flamingock.template.mongodb.mapper;

import com.mongodb.client.model.Collation;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.bson.conversions.Bson;

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
        if (value instanceof String) {
            return new org.bson.BsonString((String) value);
        } else if (value instanceof Integer) {
            return new org.bson.BsonInt32((Integer) value);
        } else if (value instanceof Long) {
            return new org.bson.BsonInt64((Long) value);
        } else if (value instanceof Double) {
            return new org.bson.BsonDouble((Double) value);
        } else if (value instanceof Boolean) {
            return new org.bson.BsonBoolean((Boolean) value);
        } else if (value instanceof Map) {
            return toBsonDocument((Map<String, Object>) value);
        }
        throw new IllegalArgumentException("Unsupported BSON type: " + value.getClass().getSimpleName());
    }
}
