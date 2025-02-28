package io.flamingock.template.mongodb.mapper;

import com.mongodb.client.model.InsertManyOptions;
import com.mongodb.client.model.InsertOneOptions;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static io.flamingock.template.mongodb.mapper.MapperUtil.getBoolean;

public class InsertOptionsMapper {

    public static InsertOneOptions mapToInertOneOptions(Map<String, Object> options) {
        InsertOneOptions insertOneOptions = new InsertOneOptions();

        if (options.containsKey("bypassDocumentValidation")) {
            insertOneOptions.bypassDocumentValidation(getBoolean(options, "bypassDocumentValidation"));
        }

        return insertOneOptions;
    }

    public static InsertManyOptions mapToInertManyOptions(Map<String, Object> options) {
        InsertManyOptions insertOneOptions = new InsertManyOptions();

        if (options.containsKey("bypassDocumentValidation")) {
            insertOneOptions.bypassDocumentValidation(getBoolean(options, "bypassDocumentValidation"));
        }

        if (options.containsKey("ordered")) {
            insertOneOptions.bypassDocumentValidation(getBoolean(options, "ordered"));
        }

        return insertOneOptions;
    }

}

