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
package io.flamingock.targetsystem.mongodb.springdata;

import com.mongodb.client.MongoDatabase;
import io.flamingock.internal.common.core.error.FlamingockException;
import io.flamingock.targetystem.mongodb.api.MongoDatabaseAccesor;
import org.springframework.data.mongodb.core.MongoTemplate;

public class MongoDBSpringDataDatabaseAccesor implements MongoDatabaseAccesor {

    private final MongoTemplate mongoTemplate;
    private MongoDatabase mongoDatabase;

    public MongoDBSpringDataDatabaseAccesor(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
        this.mongoDatabase = null;
    }

    @Override
    public MongoDatabase getDatabase() {

        if (mongoDatabase == null) {
            if (mongoTemplate == null) {
                throw new FlamingockException("The 'mongoTemplate' instance is required.");
            }
            mongoDatabase = mongoTemplate.getDb();
        }

        return mongoDatabase;
    }
}
