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
package io.flamingock.community.mongodb.sync.changes;

import com.mongodb.client.MongoDatabase;
import io.flamingock.api.annotations.Apply;
import io.flamingock.api.annotations.TargetSystem;
import io.flamingock.api.annotations.Change;
import io.flamingock.api.annotations.Rollback;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import io.mongock.api.annotations.BeforeExecution;
import io.mongock.api.annotations.RollbackBeforeExecution;
import org.bson.Document;

import java.util.stream.Collectors;
import java.util.stream.IntStream;


@TargetSystem(id = "mongodb")
@Change(id = "create-author-collection", author = "mongock")
@ChangeUnit(id = "create-author-collection", order = "000", author = "mongock")
public class _000__mongock_create_authors_collection {

    public final static int INITIAL_CLIENTS = 10;
    public final static String CLIENTS_COLLECTION_NAME = "mongockClientCollection";

    @BeforeExecution
    public void beforeExecution(MongoDatabase mongoDatabase) {

        mongoDatabase.createCollection(CLIENTS_COLLECTION_NAME);
    }

    @RollbackBeforeExecution
    public void rollbackBeforeExecution(MongoDatabase mongoDatabase) {

        mongoDatabase.getCollection(CLIENTS_COLLECTION_NAME).drop();
    }

    @Apply
    @Execution
    public void apply(MongoDatabase mongoDatabase) {

        mongoDatabase.getCollection(CLIENTS_COLLECTION_NAME)
                .insertMany(IntStream.range(0, INITIAL_CLIENTS)
                        .mapToObj(_000__mongock_create_authors_collection::getClient)
                        .collect(Collectors.toList()));
    }

    @Rollback
    @RollbackExecution
    public void rollback(MongoDatabase mongoDatabase) {
        mongoDatabase.getCollection(CLIENTS_COLLECTION_NAME).deleteMany(new Document());
    }

    private static Document getClient(int i) {
        return new Document()
                .append("name", "name-" + i)
                .append("email","email-" + i)
                .append("phone","phone" + i).
                append("country","country" + i);
    }
}