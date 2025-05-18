package io.flamingock.template.mongodb.model.operator;

import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoDatabase;
import io.flamingock.template.mongodb.model.MongoOperation;

public class CreateCollectionOperator extends MongoOperator {


    public CreateCollectionOperator(MongoDatabase mongoDatabase, MongoOperation operation) {
        super(mongoDatabase, operation, false);
    }

    @Override
    public void applyInternal(ClientSession clientSession) {
        mongoDatabase.createCollection(op.getCollection());
    }
}
