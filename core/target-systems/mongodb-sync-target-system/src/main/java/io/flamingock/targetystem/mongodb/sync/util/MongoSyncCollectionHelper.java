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
package io.flamingock.targetystem.mongodb.sync.util;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.IndexOptions;
import io.flamingock.internal.common.mongodb.CollectionHelper;
import io.flamingock.internal.common.mongodb.DocumentHelper;
import org.bson.Document;


public class MongoSyncCollectionHelper implements CollectionHelper<MongoSyncDocumentHelper> {

    private final MongoCollection<Document> collection;


    public MongoSyncCollectionHelper(MongoCollection<Document> collection) {
        this.collection = collection;
    }

    @Override
    public String getCollectionName() {
        return collection.getNamespace().getCollectionName();
    }

    @Override
    public Iterable<DocumentHelper> listIndexes() {
        return collection.listIndexes().map(MongoSyncDocumentHelper::new);
    }

    @Override
    public String createUniqueIndex(MongoSyncDocumentHelper uniqueIndexDocument) {
        return collection.createIndex(uniqueIndexDocument.getDocument(), new IndexOptions().unique(true));
    }

    @Override
    public void dropIndex(String indexName) {
        collection.dropIndex(indexName);
    }

    @Override
    public void deleteMany(MongoSyncDocumentHelper documentWrapper) {
        collection.deleteMany(documentWrapper.getDocument());
    }


}
