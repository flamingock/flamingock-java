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
import com.mongodb.client.model.IndexOptions;


import java.util.Map;

import static io.flamingock.template.mongodb.mapper.MapperUtil.getBoolean;
import static io.flamingock.template.mongodb.mapper.MapperUtil.getBson;
import static io.flamingock.template.mongodb.mapper.MapperUtil.getCollation;
import static io.flamingock.template.mongodb.mapper.MapperUtil.getDouble;
import static io.flamingock.template.mongodb.mapper.MapperUtil.getInteger;
import static io.flamingock.template.mongodb.mapper.MapperUtil.getLong;
import static io.flamingock.template.mongodb.mapper.MapperUtil.getString;

import java.util.concurrent.TimeUnit;


public class IndexOptionsMapper {

    public static IndexOptions mapToIndexOptions(Map<String, Object> options) {
        IndexOptions indexOptions = new IndexOptions();

        if (options.containsKey("background")) {
            indexOptions.background(getBoolean(options, "background"));
        }
        if (options.containsKey("unique")) {
            indexOptions.unique(getBoolean(options, "unique"));
        }
        if (options.containsKey("name")) {
            indexOptions.name(getString(options, "name"));
        }
        if (options.containsKey("sparse")) {
            indexOptions.sparse(getBoolean(options, "sparse"));
        }
        if (options.containsKey("expireAfterSeconds")) {
            indexOptions.expireAfter(getLong(options, "expireAfterSeconds"), TimeUnit.SECONDS);
        }
        if (options.containsKey("version")) {
            indexOptions.version(getInteger(options, "version"));
        }
        if (options.containsKey("weights")) {
            indexOptions.weights(getBson(options, "weights"));
        }
        if (options.containsKey("defaultLanguage")) {
            indexOptions.defaultLanguage(getString(options, "defaultLanguage"));
        }
        if (options.containsKey("languageOverride")) {
            indexOptions.languageOverride(getString(options, "languageOverride"));
        }
        if (options.containsKey("textVersion")) {
            indexOptions.textVersion(getInteger(options, "textVersion"));
        }
        if (options.containsKey("sphereVersion")) {
            indexOptions.sphereVersion(getInteger(options, "sphereVersion"));
        }
        if (options.containsKey("bits")) {
            indexOptions.bits(getInteger(options, "bits"));
        }
        if (options.containsKey("min")) {
            indexOptions.min(getDouble(options, "min"));
        }
        if (options.containsKey("max")) {
            indexOptions.max(getDouble(options, "max"));
        }
        if (options.containsKey("bucketSize")) {
            throw new UnsupportedOperationException("bulkSize option is not supported in MongoDB driver versions 4.4.0 and above");
        }
        if (options.containsKey("storageEngine")) {
            indexOptions.storageEngine(getBson(options, "storageEngine"));
        }
        if (options.containsKey("partialFilterExpression")) {
            indexOptions.partialFilterExpression(getBson(options, "partialFilterExpression"));
        }
        if (options.containsKey("collation")) {
            indexOptions.collation(getCollation(options, "collation"));
        }
        if (options.containsKey("wildcardProjection")) {
            throw new UnsupportedOperationException("wildcardProjection option is not supported in MongoDB driver versions 4.1.0 and above");
        }
        if (options.containsKey("hidden")) {
            throw new UnsupportedOperationException("hidden option is not supported in MongoDB driver versions 4.1.0 and above");
        }

        return indexOptions;
    }


    // Utility methods for safe type checking with exception handling


}

