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
package io.flamingock.targetystem.mongodb.sync;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import io.flamingock.internal.common.core.error.FlamingockException;
import io.flamingock.targetystem.mongodb.api.MongoDatabaseAccesor;

public class MongoDBSyncDatabaseAccesor implements MongoDatabaseAccesor {

    private final MongoClient mongoClient;
    private final String databaseName;
    private MongoDatabase mongoDatabase;

    public MongoDBSyncDatabaseAccesor(MongoClient mongoClient, String databaseName) {
        this.mongoClient = mongoClient;
        this.databaseName = databaseName;
        this.mongoDatabase = null;
    }

    @Override
    public MongoDatabase getDatabase() {

        if (mongoDatabase == null) {
            if (mongoClient == null) {
                throw new FlamingockException("The 'mongoClient' instance is required.");
            }
            if (databaseName == null || databaseName.trim().isEmpty()) {
                throw new FlamingockException("The 'databaseName' property is required.");
            }
            mongoDatabase = mongoClient.getDatabase(databaseName);
        }

        return mongoDatabase;
    }
}
