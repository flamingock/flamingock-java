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
import io.flamingock.core.kit.TestKit;
import io.flamingock.internal.core.community.driver.LocalDriver;

public class MongoSyncTestKit {

    /**
     * Create a new MongoSyncTestKit with MongoDB client and database
     */
    public static TestKit create(LocalDriver driver, MongoDatabase database) {
        MongoSyncAuditStorage auditStorage = new MongoSyncAuditStorage(database);
        MongoSyncLockStorage lockStorage = new MongoSyncLockStorage(database);
        return TestKit.create(auditStorage, lockStorage, driver);
    }
}
