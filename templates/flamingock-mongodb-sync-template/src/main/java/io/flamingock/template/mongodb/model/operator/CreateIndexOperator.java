package io.flamingock.template.mongodb.model.operator;

import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;
import io.flamingock.template.mongodb.model.MongoOperation;
import io.flamingock.template.mongodb.mapper.IndexOptionsMapper;

public class CreateIndexOperator extends MongoOperator {


    public CreateIndexOperator(MongoDatabase mongoDatabase, MongoOperation operation) {
        super(mongoDatabase, operation, true);
    }

    @Override
    protected void applyInternal(ClientSession clientSession) {
        if (clientSession != null) {
            logger.warn("MongoDB does not support transactions for createCollection operation. Ignoring transactional flag.");
        }
        IndexOptions indexOptions = IndexOptionsMapper.mapToIndexOptions(op.getOptions());
        mongoDatabase.getCollection(op.getCollection())
                .createIndex(op.getKeys(), indexOptions);
    }

}
