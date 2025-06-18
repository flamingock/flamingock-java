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

package io.flamingock.community.mongodb.springdata.internal;

import com.mongodb.client.MongoDatabase;
import io.flamingock.internal.util.TimeService;
import io.flamingock.community.mongodb.sync.internal.MongoSync4LockService;
import io.flamingock.community.mongodb.sync.internal.ReadWriteConfiguration;

public class SpringDataMongoLockService extends MongoSync4LockService {

    protected SpringDataMongoLockService(MongoDatabase mongoDatabase, String lockCollectionName, ReadWriteConfiguration readWriteConfiguration) {
        super(mongoDatabase, lockCollectionName, readWriteConfiguration, TimeService.getDefault());
    }
}