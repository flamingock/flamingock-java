/*
 * Copyright 2023 Flamingock (https://oss.flamingock.io)
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

package io.flamingock.importer.cloud.mongodb.v4.legacy.mongock;

import com.mongodb.client.MongoDatabase;
import io.mongock.api.annotations.*;
import org.bson.Document;

import java.util.stream.Collectors;
import java.util.stream.IntStream;


@ChangeUnit(id = "client-initializer", order = "1", author = "mongock")
public class ClientInitializerChangeUnit {

    public final static int INITIAL_CLIENTS = 10;
    public final static String CLIENTS_COLLECTION_NAME = "mongockClientCollection";

    private static Document getClient(int i) {
        return new Document()
                .append("name", "name-" + i)
                .append("email", "email-" + i)
                .append("phone", "phone" + i).
                append("country", "country" + i);
    }

    @BeforeExecution
    public void beforeExecution(MongoDatabase mongoDatabase) {

        mongoDatabase.createCollection(CLIENTS_COLLECTION_NAME);
    }

    @RollbackBeforeExecution
    public void rollbackBeforeExecution(MongoDatabase mongoDatabase) {

        mongoDatabase.getCollection(CLIENTS_COLLECTION_NAME).drop();
    }

    @Execution
    public void execution(MongoDatabase mongoDatabase) {

        mongoDatabase.getCollection(CLIENTS_COLLECTION_NAME)
                .insertMany(IntStream.range(0, INITIAL_CLIENTS)
                        .mapToObj(ClientInitializerChangeUnit::getClient)
                        .collect(Collectors.toList()));
    }

    @RollbackExecution
    public void rollbackExecution(MongoDatabase mongoDatabase) {
        mongoDatabase.getCollection(CLIENTS_COLLECTION_NAME).deleteMany(new Document());
    }
}
