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

        IndexOptions indexOptions = IndexOptionsMapper.mapToIndexOptions(op.getOptions());
        if (clientSession != null) {
            mongoDatabase
                    .getCollection(op.getCollection())
                    .createIndex(clientSession, op.getKeys(), indexOptions);
        } else {
            mongoDatabase
                    .getCollection(op.getCollection())
                    .createIndex(op.getKeys(), indexOptions);
        }
    }

}
