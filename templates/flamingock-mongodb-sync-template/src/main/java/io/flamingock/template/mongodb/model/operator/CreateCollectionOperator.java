package io.flamingock.template.mongodb.model.operator;

import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoDatabase;
import io.flamingock.template.mongodb.model.MongoOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CreateCollectionOperator extends MongoOperator {
    protected static final Logger logger = LoggerFactory.getLogger("Flamingock-MongoDB-Template");


    public CreateCollectionOperator(MongoDatabase mongoDatabase, MongoOperation operation) {
        super(mongoDatabase, operation, false);
    }

    @Override
    public void applyInternal(ClientSession clientSession) {
        if (clientSession != null) {
            logger.warn("MongoDB does not support transactions for createCollection operation. Ignoring transactional flag.");
        }
        mongoDatabase.createCollection(op.getCollection());
    }

}
