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
package io.flamingock.mongodb.kit;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import io.flamingock.core.kit.AbstractTestKit;
import io.flamingock.core.kit.audit.AuditStorage;
import io.flamingock.core.kit.lock.LockStorage;
import io.flamingock.internal.core.community.store.LocalAuditStore;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class MongoSyncTestKit extends AbstractTestKit {
    private static final Set<String> SYSTEM_DATABASES =
            new HashSet<>(Arrays.asList("admin", "config", "local"));

    private final MongoClient mongoClient;

    public MongoSyncTestKit(AuditStorage auditStorage, LockStorage lockStorage, LocalAuditStore driver, MongoClient mongoClient) {
        super(auditStorage, lockStorage, driver);
        this.mongoClient = mongoClient;
    }

    @Override
    public void cleanUp() {
        mongoClient.listDatabaseNames().forEach(dbName -> {
            if (!SYSTEM_DATABASES.contains(dbName)) {
                mongoClient.getDatabase(dbName).drop();
            }
        });
    }

    /**
     * Create a new MongoSyncTestKit with MongoDB client and database
     */
    public static MongoSyncTestKit create(LocalAuditStore driver, MongoClient mongoClient, MongoDatabase database) {
        MongoSyncAuditStorage auditStorage = new MongoSyncAuditStorage(database);
        MongoSyncLockStorage lockStorage = new MongoSyncLockStorage(database);
        return new MongoSyncTestKit(auditStorage, lockStorage, driver, mongoClient);
    }
}
