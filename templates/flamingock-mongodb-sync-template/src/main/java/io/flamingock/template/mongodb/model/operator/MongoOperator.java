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
import com.mongodb.client.MongoDatabase;
import io.flamingock.template.mongodb.model.MongoOperation;
import io.flamingock.internal.util.log.FlamingockLoggerFactory;
import org.slf4j.Logger;

public abstract class MongoOperator {
    protected static final Logger logger = FlamingockLoggerFactory.getLogger("MongoTemplate");

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
                                "Recommend marking Change as transactional.",
                        simpleName);
            } else {
                logger.debug("Applying operation [{}] with transaction: ", simpleName);
            }
        } else {
            if(withClientSession) {
                logger.info("{} is not transactional, but Change has been marked as transactional. Transaction ignored.", simpleName);
            } else {
                logger.debug("Applying non-transactional operation [{}]: ", simpleName);
            }
        }
    }


    protected abstract void applyInternal(ClientSession clientSession);
}
