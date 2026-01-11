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

import com.mongodb.client.model.UpdateOptions;

import java.util.Map;

import static io.flamingock.template.mongodb.mapper.MapperUtil.getBoolean;
import static io.flamingock.template.mongodb.mapper.MapperUtil.getCollation;

public final class UpdateOptionsMapper {

    private UpdateOptionsMapper() {}

    public static UpdateOptions mapToUpdateOptions(Map<String, Object> options) {
        UpdateOptions updateOptions = new UpdateOptions();

        if (options.containsKey("upsert")) {
            updateOptions.upsert(getBoolean(options, "upsert"));
        }

        if (options.containsKey("bypassDocumentValidation")) {
            updateOptions.bypassDocumentValidation(getBoolean(options, "bypassDocumentValidation"));
        }

        if (options.containsKey("collation")) {
            updateOptions.collation(getCollation(options, "collation"));
        }

        if (options.containsKey("arrayFilters")) {
            Object arrayFilters = options.get("arrayFilters");
            if (arrayFilters instanceof java.util.List) {
                java.util.List<org.bson.conversions.Bson> bsonFilters = new java.util.ArrayList<>();
                for (Object filter : (java.util.List<?>) arrayFilters) {
                    if (filter instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> filterMap = (Map<String, Object>) filter;
                        bsonFilters.add(MapperUtil.toBsonDocument(filterMap));
                    } else if (filter instanceof org.bson.conversions.Bson) {
                        bsonFilters.add((org.bson.conversions.Bson) filter);
                    }
                }
                updateOptions.arrayFilters(bsonFilters);
            }
        }

        return updateOptions;
    }
}
