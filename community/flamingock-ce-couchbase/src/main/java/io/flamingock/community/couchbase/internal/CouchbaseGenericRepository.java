/*
 * Copyright 2023 Flamingock (https://oss.flamingock.io)
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

package io.flamingock.community.couchbase.internal;

import io.flamingock.community.couchbase.internal.util.CollectionIdentifierUtil;
import io.flamingock.community.couchbase.internal.util.N1QLQueryProvider;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.Collection;
import com.couchbase.client.java.json.JsonArray;
import com.couchbase.client.java.json.JsonObject;
import com.couchbase.client.java.manager.query.CreateQueryIndexOptions;
import com.couchbase.client.java.manager.query.DropQueryIndexOptions;
import com.couchbase.client.java.manager.query.QueryIndex;
import com.couchbase.client.java.query.QueryOptions;
import com.couchbase.client.java.query.QueryScanConsistency;

import static io.flamingock.community.couchbase.internal.CouchbaseConstants.INDEX_NAME;

public class CouchbaseGenericRepository {

    private static final Logger logger = LoggerFactory.getLogger(CouchbaseGenericRepository.class);

    protected final Collection collection;
    protected final Cluster cluster;
    protected final Set<String> queryFields;
    private boolean collectionIndexed = false;

    protected CouchbaseGenericRepository(Cluster cluster, Collection collection, Set<String> queryFields) {
        this.cluster = cluster;
        this.collection = collection;
        this.queryFields = queryFields;
    }

    protected void initialize(boolean indexCreation) {
        if (!collectionIndexed) {
            ensureIndex(indexCreation);
            collectionIndexed = true;
        }
    }

    /**
     * Ensures that the collection has an index with all the required fields if not,
     * creates it.
     */
    private void ensureIndex(boolean indexCreation) {
        if (!queryFields.isEmpty() && !isIndexFine()) {
            if (indexCreation) {
                createRequiredIndexes();
            } else {
                if (hasPrimaryKey()) {
                    logger.debug(
                            "Index creation not allowed, but primary key exists for collection {} (execution may be slow)",
                            collection.name());
                } else {
                    throw new RuntimeException(
                            "Index creation not allowed, but not created or wrongly created (at least primary key should be created) for collection "
                                    + collection.name());
                }
            }
        }
    }

    /**
     * Creates secondary index with all the required query fields.
     * In case index already exists, it will be dropped and recreated.
     */
    private void createRequiredIndexes() {
        logger.debug("Dropping index {} for collection {}", INDEX_NAME, collection.name());
        if (isDefaultCollection()) {
            cluster.queryIndexes().dropIndex(collection.bucketName(), INDEX_NAME,
                    DropQueryIndexOptions.dropQueryIndexOptions().ignoreIfNotExists(true));
        } else {
            collection.queryIndexes().dropIndex(INDEX_NAME,
                    DropQueryIndexOptions.dropQueryIndexOptions().ignoreIfNotExists(true));
        }

        logger.debug("Recreating index {} for collection {} on fields {}", INDEX_NAME, collection.name(), queryFields);
        if (isDefaultCollection()) {
            cluster.queryIndexes().createIndex(collection.bucketName(), INDEX_NAME, queryFields,
                    CreateQueryIndexOptions.createQueryIndexOptions().ignoreIfExists(true));
        } else {
            collection.queryIndexes().createIndex(INDEX_NAME, queryFields,
                    CreateQueryIndexOptions.createQueryIndexOptions().ignoreIfExists(true));
        }
    }

    /**
     * Checks if the collection has an index with all the required fields.
     * Note: Couchbase does not support unique indexes, so we can't check if the
     * index is unique.
     * 
     * @return <code>true</code> if there is at least one index with required
     *         fields.
     */
    private boolean isIndexFine() {
        List<QueryIndex> indexes = isDefaultCollection() ? cluster.queryIndexes().getAllIndexes(collection.bucketName())
                .stream().filter(index -> collection.bucketName().equals(index.bucketName()))
                .collect(Collectors.toList()) : collection.queryIndexes().getAllIndexes();
        for (QueryIndex index : indexes) {
            JsonArray indexKeys = index.indexKey();
            // indexes are escaped, so get rid of "`" chars
            List<String> indexFields = indexKeys.toList()
                    .stream()
                    .map(String::valueOf)
                    .map(s -> s.replaceAll("`", ""))
                    .collect(Collectors.toList());
            if (indexFields.containsAll(queryFields)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns tru if the collection has a primary key.
     * 
     * @return true if the collection has a primary key.
     */
    private boolean hasPrimaryKey() {
        return isDefaultCollection()
                ? cluster.queryIndexes().getAllIndexes(collection.bucketName()).stream()
                        .anyMatch(index -> index.primary() && collection.bucketName().equals(index.bucketName()))
                : collection.queryIndexes().getAllIndexes().stream().anyMatch(QueryIndex::primary);
    }

    /**
     * Only for testing
     */
    protected void deleteAll() {
        if (!hasPrimaryKey()) {
            // to delete all we at least need the primary key index
            if (isDefaultCollection()) {
                cluster.queryIndexes().createPrimaryIndex(collection.bucketName());
            } else {
                collection.queryIndexes().createPrimaryIndex();
            }
        }
        cluster.query(
                N1QLQueryProvider.deleteAllChangesQuery(collection.bucketName(), collection.scopeName(),
                        collection.name()),
                QueryOptions.queryOptions().scanConsistency(QueryScanConsistency.REQUEST_PLUS));
    }

    /**
     * By default, couchbase does not support custom object serialization, like Date
     * or Optional.
     * Still we can register custom Jackson modules to support that, but that will
     * also change the overall
     * couchbase client functionality. So to keep it simple just try to convert
     * values in place.
     * 
     * @param document The document to which to add a given key/value pair.
     * @param key      The key of the object in document.
     * @param value    The value of the object in document.
     */
    protected void addField(JsonObject document, String key, Object value) {
        if (value instanceof Date) {
            document.put(key, ((Date) value).getTime());
        } else if (value instanceof Optional) {
            Optional optional = (Optional) value;
            if (optional.isPresent()) {
                addField(document, key, optional.get());
            }
        } else {
            try {
                document.put(key, value);
            } catch (IllegalArgumentException e) {
                throw new RuntimeException("Unsupported Couchbase type " + value.getClass().getName());
            }
        }
    }

    private boolean isDefaultCollection() {
        return CollectionIdentifierUtil.isDefaultCollection(collection.scopeName(), collection.name());
    }
}
