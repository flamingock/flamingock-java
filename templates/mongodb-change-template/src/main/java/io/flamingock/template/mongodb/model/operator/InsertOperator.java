package io.flamingock.template.mongodb.model.operator;

import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.InsertManyOptions;
import com.mongodb.client.model.InsertOneOptions;
import io.flamingock.template.mongodb.mapper.InsertOptionsMapper;
import io.flamingock.template.mongodb.model.MongoOperation;
import org.bson.Document;

import java.util.List;

public class InsertOperator extends MongoOperator {


    public InsertOperator(MongoDatabase mongoDatabase, MongoOperation operation) {
        super(mongoDatabase, operation, true);
    }

    @Override
    protected void applyInternal(ClientSession clientSession) {
        MongoCollection<Document> collection = mongoDatabase.getCollection(op.getCollection());
        if(op.getDocuments() == null || op.getDocuments().size() == 0) {
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
            if(op.getOptions().size() != 0) {
                InsertManyOptions insertManyOptions = InsertOptionsMapper.mapToInertManyOptions(op.getOptions());
                collection.insertMany(clientSession, op.getDocuments(), insertManyOptions);
            } else {
                collection.insertMany(clientSession, op.getDocuments());
            }

        } else {
            if(op.getOptions().size() != 0) {
                InsertManyOptions insertManyOptions = InsertOptionsMapper.mapToInertManyOptions(op.getOptions());
                collection.insertMany(op.getDocuments(), insertManyOptions);
            } else {
                collection.insertMany(op.getDocuments());
            }
        }
    }

    private void insertOne(ClientSession clientSession, MongoCollection<Document> collection) {

        if(clientSession != null) {
            if(op.getOptions().size() != 0) {
                InsertOneOptions insertOneOptions = InsertOptionsMapper.mapToInertOneOptions(op.getOptions());
                collection.insertOne(clientSession, op.getDocuments().get(0), insertOneOptions);
            } else {
                collection.insertOne(clientSession, op.getDocuments().get(0));
            }

        } else {
            if(op.getOptions().size() != 0) {
                InsertOneOptions insertOneOptions = InsertOptionsMapper.mapToInertOneOptions(op.getOptions());
                collection.insertOne(op.getDocuments().get(0), insertOneOptions);
            } else {
                collection.insertOne(op.getDocuments().get(0));
            }
        }
    }
}
