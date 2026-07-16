/*
 * Copyright 2026 Flamingock (https://www.flamingock.io)
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
package io.flamingock.mongodb.reactive.kit;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoDatabase;
import io.flamingock.core.kit.AbstractTestKit;
import io.flamingock.core.kit.audit.AuditStorage;
import io.flamingock.core.kit.lock.LockStorage;
import io.flamingock.internal.core.external.store.CommunityAuditStore;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class MongoDBReactiveTestKit extends AbstractTestKit {
    private static final Set<String> SYSTEM_DATABASES =
            new HashSet<>(Arrays.asList("admin", "config", "local"));

    private final MongoClient mongoClient;

    public MongoDBReactiveTestKit(AuditStorage auditStorage, LockStorage lockStorage, CommunityAuditStore auditStore, MongoClient mongoClient) {
        super(auditStorage, lockStorage, auditStore);
        this.mongoClient = mongoClient;
    }

    @Override
    public void cleanUp() {
        PublisherSync.collect(mongoClient.listDatabaseNames()).forEach(dbName -> {
            if (!SYSTEM_DATABASES.contains(dbName)) {
                PublisherSync.complete(mongoClient.getDatabase(dbName).drop());
            }
        });
    }

    /**
     * Create a new MongoDBReactiveTestKit with MongoDB client and database.
     */
    public static MongoDBReactiveTestKit create(CommunityAuditStore auditStore, MongoClient mongoClient, MongoDatabase database) {
        MongoDBReactiveAuditStorage auditStorage = new MongoDBReactiveAuditStorage(database);
        MongoDBReactiveLockStorage lockStorage = new MongoDBReactiveLockStorage(database);
        return new MongoDBReactiveTestKit(auditStorage, lockStorage, auditStore, mongoClient);
    }
}
