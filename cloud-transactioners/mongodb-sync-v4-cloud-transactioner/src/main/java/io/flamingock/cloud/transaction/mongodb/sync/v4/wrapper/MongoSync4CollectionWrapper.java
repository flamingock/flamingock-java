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

package io.flamingock.cloud.transaction.mongodb.sync.v4.wrapper;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.IndexOptions;
import io.flamingock.oss.driver.common.mongodb.CollectionWrapper;
import io.flamingock.oss.driver.common.mongodb.DocumentWrapper;
import org.bson.Document;


public class MongoSync4CollectionWrapper implements CollectionWrapper<MongoSync4DocumentWrapper> {

    private final MongoCollection<Document> collection;


    public MongoSync4CollectionWrapper(MongoCollection<Document> collection) {
        this.collection = collection;
    }

    @Override
    public String getCollectionName() {
        return collection.getNamespace().getCollectionName();
    }

    @Override
    public Iterable<DocumentWrapper> listIndexes() {
        return collection.listIndexes().map(MongoSync4DocumentWrapper::new);
    }

    @Override
    public String createUniqueIndex(MongoSync4DocumentWrapper uniqueIndexDocument) {
        return collection.createIndex(uniqueIndexDocument.getDocument(), new IndexOptions().unique(true));
    }

    @Override
    public void dropIndex(String indexName) {
        collection.dropIndex(indexName);
    }

    @Override
    public void deleteMany(MongoSync4DocumentWrapper documentWrapper) {
        collection.deleteMany(documentWrapper.getDocument());
    }


}
