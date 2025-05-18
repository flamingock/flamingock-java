package io.flamingock.template.mongodb.model.operator;

import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoDatabase;
import io.flamingock.template.mongodb.model.MongoOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class MongoOperator {
    protected static final Logger logger = LoggerFactory.getLogger(MongoOperator.class);

    protected final MongoDatabase mongoDatabase;
    protected final MongoOperation op;
    protected final boolean transactional;

    protected MongoOperator(MongoDatabase mongoDatabase, MongoOperation op, boolean transactional) {
        this.op = op;
        this.mongoDatabase = mongoDatabase;
        this.transactional = transactional;
    }

    public final void apply(ClientSession clientSession) {
        logOperation(clientSession != null);
        applyInternal(clientSession);
    }

    private void logOperation(boolean withClientSession) {
        String simpleName = getClass().getSimpleName();

        if (transactional) {
            if (withClientSession) {
                logger.warn("{} is a transactional operation but is not being applied within a transaction. " +
                                "Recommend marking ChangeUnit as transactional.",
                        simpleName);
            } else {
                logger.debug("Applying operation [{}] with transaction: ", simpleName);
            }
        } else {
            if(withClientSession) {
                logger.info("{} is not transactional, but ChangeUnit has been marked as transactional. Transaction ignored.", simpleName);
            } else {
                logger.debug("Applying non-transactional operation [{}]: ", simpleName);
            }
        }
    }


    protected abstract void applyInternal(ClientSession clientSession);
}
