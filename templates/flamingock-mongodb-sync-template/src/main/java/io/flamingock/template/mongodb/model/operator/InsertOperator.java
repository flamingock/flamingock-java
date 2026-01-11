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
package io.flamingock.template.mongodb.model.operator;

import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.InsertManyOptions;
import com.mongodb.client.model.InsertOneOptions;
import io.flamingock.template.mongodb.mapper.InsertOptionsMapper;
import io.flamingock.template.mongodb.model.MongoOperation;
import org.bson.Document;

public class InsertOperator extends MongoOperator {


    public InsertOperator(MongoDatabase mongoDatabase, MongoOperation operation) {
        super(mongoDatabase, operation, true);
    }

    @Override
    protected void applyInternal(ClientSession clientSession) {
        MongoCollection<Document> collection = mongoDatabase.getCollection(op.getCollection());
        if(op.getDocuments() == null || op.getDocuments().isEmpty()) {
            return;
        }

        if(op.getDocuments().size() == 1) {
            insertOne(clientSession, collection);
        } else {
            insertMany(clientSession, collection);
        }
    }

    private void insertMany(ClientSession clientSession, MongoCollection<Document> collection) {
        if(clientSession != null) {
            if(!op.getOptions().isEmpty()) {
                InsertManyOptions insertManyOptions = InsertOptionsMapper.mapToInsertManyOptions(op.getOptions());
                collection.insertMany(clientSession, op.getDocuments(), insertManyOptions);
            } else {
                collection.insertMany(clientSession, op.getDocuments());
            }

        } else {
            if(!op.getOptions().isEmpty()) {
                InsertManyOptions insertManyOptions = InsertOptionsMapper.mapToInsertManyOptions(op.getOptions());
                collection.insertMany(op.getDocuments(), insertManyOptions);
            } else {
                collection.insertMany(op.getDocuments());
            }
        }
    }

    private void insertOne(ClientSession clientSession, MongoCollection<Document> collection) {

        if(clientSession != null) {
            if(!op.getOptions().isEmpty()) {
                InsertOneOptions insertOneOptions = InsertOptionsMapper.mapToInsertOneOptions(op.getOptions());
                collection.insertOne(clientSession, op.getDocuments().get(0), insertOneOptions);
            } else {
                collection.insertOne(clientSession, op.getDocuments().get(0));
            }

        } else {
            if(!op.getOptions().isEmpty()) {
                InsertOneOptions insertOneOptions = InsertOptionsMapper.mapToInsertOneOptions(op.getOptions());
                collection.insertOne(op.getDocuments().get(0), insertOneOptions);
            } else {
                collection.insertOne(op.getDocuments().get(0));
            }
        }
    }
}
