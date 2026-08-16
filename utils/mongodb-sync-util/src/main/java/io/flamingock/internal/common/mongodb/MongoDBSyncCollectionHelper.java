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
package io.flamingock.internal.common.mongodb;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.IndexOptions;
import org.bson.Document;


public class MongoDBSyncCollectionHelper implements CollectionHelper<MongoDBDocumentHelper> {

    private final MongoCollection<Document> collection;


    public MongoDBSyncCollectionHelper(MongoCollection<Document> collection) {
        this.collection = collection;
    }

    @Override
    public String getCollectionName() {
        return collection.getNamespace().getCollectionName();
    }

    @Override
    public Iterable<DocumentHelper> listIndexes() {
        return collection.listIndexes().map(MongoDBDocumentHelper::new);
    }

    @Override
    public String createIndex(MongoDBDocumentHelper keyDocument,
                              String name,
                              boolean unique,
                              MongoDBDocumentHelper partialFilterExpression) {
        IndexOptions options = new IndexOptions().unique(unique);
        if (name != null) {
            options.name(name);
        }
        if (partialFilterExpression != null) {
            options.partialFilterExpression(partialFilterExpression.getDocument());
        }
        return collection.createIndex(keyDocument.getDocument(), options);
    }

    @Override
    public void dropIndex(String indexName) {
        collection.dropIndex(indexName);
    }

    @Override
    public void deleteMany(MongoDBDocumentHelper documentWrapper) {
        collection.deleteMany(documentWrapper.getDocument());
    }


}
